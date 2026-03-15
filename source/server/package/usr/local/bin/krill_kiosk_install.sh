#!/bin/bash
#
# Krill Kiosk Installation Script
# Transforms a headless Raspberry Pi 5 into a fullscreen Krill kiosk
#
# Supported hardware:
#   - Raspberry Pi 5 with HDMI touchscreen
#
# Usage: sudo krill_kiosk_install.sh [--uninstall]
#
# Runs Chromium in fullscreen kiosk mode over X11 with the cursor disabled.
# X11 is used instead of Wayland because Xorg's -nocursor flag reliably
# hides the cursor at the server level — no cursor themes or unclutter hacks.
#
#

set -e

KRILL_USER="${KRILL_KIOSK_USER:-kiosk}"
KRILL_PORT="8442"
KRILL_CERT="/etc/krill/certs/krill.crt"
KRILL_API_KEY_FILE="/etc/krill/credentials/api_key"
KIOSK_SERVICE="/etc/systemd/system/krill-kiosk.service"
CHROMIUM_POLICIES_DIR="/etc/chromium/policies/managed"
LAUNCHER_SCRIPT="/usr/local/bin/krill-kiosk-launcher"
ONBOARD_AUTOSTART_DIR="/etc/xdg/autostart"
ONBOARD_DCONF_PROFILE="/etc/dconf/profile/kiosk"
ONBOARD_DCONF_DB_DIR="/etc/dconf/db/kiosk.d"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() {
    echo -e "${GREEN}🦐${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}🦐 ⚠${NC} $1"
}

log_error() {
    echo -e "${RED}🦐 ❌${NC} $1"
}

check_root() {
    if [ "$EUID" -ne 0 ]; then
        log_error "This script must be run as root (use sudo)"
        exit 1
    fi
}

prompt_tos_acceptance() {
    echo "────────────────────────────────────────────────"
    echo ""
    echo "  Kiosk mode will automatically connect the"
    echo "  browser to the Krill server, bypassing the"
    echo "  first-time setup screen."
    echo ""
    echo "  Before proceeding, please review the Krill"
    echo "  Terms of Service and Privacy Policy at:"
    echo ""
    echo "    https://krillswarm.com/categories/privacy-and-terms-of-service/"
    echo ""
    echo "────────────────────────────────────────────────"
    echo ""

    read -r -p "🦐 Do you accept the Terms of Service? [y/n] " response
    case "$response" in
        [yY]|[yY][eE][sS])
            log_info "Terms of Service accepted"
            ;;
        *)
            log_error "You must accept the Terms of Service to proceed"
            exit 1
            ;;
    esac
}

check_raspberry_pi_5() {
    if [ ! -f /proc/device-tree/model ]; then
        log_error "This script is designed for Raspberry Pi 5 only"
        exit 1
    fi

    local MODEL
    MODEL=$(tr -d '\0' < /proc/device-tree/model)

    if ! echo "$MODEL" | grep -q "Raspberry Pi 5"; then
        log_error "Unsupported hardware: $MODEL"
        log_error "This script only supports Raspberry Pi 5 with an HDMI touchscreen"
        exit 1
    fi

    log_info "Detected: $MODEL"
}

check_no_desktop() {
    for dm in gdm gdm3 sddm lightdm xdm lxdm nodm; do
        if systemctl is-active --quiet "$dm" 2>/dev/null; then
            log_error "Desktop environment detected ($dm is running)"
            log_error "This script requires Raspberry Pi OS Lite (no desktop)"
            log_error "On desktop systems, just run: chromium --kiosk $KRILL_URL"
            exit 1
        fi
    done

    for de in gnome-session plasma_session xfce4-session lxsession mate-session; do
        if pgrep -x "$de" >/dev/null 2>&1; then
            log_error "Desktop session detected ($de is running)"
            log_error "This script requires Raspberry Pi OS Lite (no desktop)"
            exit 1
        fi
    done

    log_info "No desktop environment detected — good"
}

check_hdmi() {
    if [ ! -e /dev/dri/card0 ] && [ ! -e /dev/dri/card1 ]; then
        log_error "No DRM display device found"
        log_error "This script requires an HDMI display connected to the Pi 5"
        exit 1
    fi

    log_info "HDMI display detected"
}

check_krill_running() {
    if ! systemctl is-active --quiet krill.service; then
        log_warn "Krill service is not running. Starting it..."
        systemctl start krill.service
        sleep 3
    fi

    if ! systemctl is-active --quiet krill.service; then
        log_error "Failed to start Krill service. Check: journalctl -u krill.service"
        exit 1
    fi
    log_info "Krill service is running"
}

read_api_key() {
    if [ ! -f "$KRILL_API_KEY_FILE" ]; then
        log_error "API key file not found at $KRILL_API_KEY_FILE"
        log_error "Please ensure the krill package is properly installed"
        exit 1
    fi

    KRILL_API_KEY=$(cat "$KRILL_API_KEY_FILE" | tr -d '[:space:]')

    if [ -z "$KRILL_API_KEY" ]; then
        log_error "API key file is empty: $KRILL_API_KEY_FILE"
        exit 1
    fi

    KRILL_URL="https://localhost:${KRILL_PORT}?api_key=${KRILL_API_KEY}"
    log_info "API key loaded from $KRILL_API_KEY_FILE"
    touch /etc/krill/kiosk # Create a marker file to indicate kiosk mode is set up
    chown krill:krill /etc/krill/kiosk
}

disable_wayland() {
    # If Cage or any Wayland compositor is installed from a previous kiosk setup, stop it
    if systemctl is-active --quiet krill-kiosk.service 2>/dev/null; then
        log_info "Stopping existing kiosk service..."
        systemctl stop krill-kiosk.service 2>/dev/null || true
        systemctl disable krill-kiosk.service 2>/dev/null || true
    fi

    # Remove Cage if installed — we use X11 instead
    if dpkg -l cage 2>/dev/null | grep -q "^ii"; then
        log_info "Removing Cage (Wayland compositor) — using X11 instead..."
        DEBIAN_FRONTEND=noninteractive apt-get remove -y -qq cage || true
    fi

    # Stop seatd if running (only needed for Wayland)
    if systemctl is-active --quiet seatd.service 2>/dev/null; then
        systemctl stop seatd.service 2>/dev/null || true
        systemctl disable seatd.service 2>/dev/null || true
    fi
}

install_packages() {
    log_info "Updating package lists..."
    apt-get update -qq

    log_info "Installing kiosk packages..."
    DEBIAN_FRONTEND=noninteractive apt-get install -y \
        xserver-xorg \
        xserver-xorg-input-evdev \
        xserver-xorg-input-libinput \
        xinit \
        x11-xserver-utils \
        chromium \
        fonts-liberation \
        fonts-dejavu-core \
        libnss3-tools \
        ca-certificates \
        onboard \
        dconf-cli \
        at-spi2-core

    log_info "Packages installed"
}

create_kiosk_user() {
    if id "$KRILL_USER" &>/dev/null; then
        log_info "Kiosk user '$KRILL_USER' already exists"
    else
        log_info "Creating kiosk user '$KRILL_USER'..."
        useradd -m -s /bin/bash -G video,audio,input,render "$KRILL_USER"
    fi

    for group in video audio input render tty; do
        if getent group "$group" >/dev/null 2>&1; then
            usermod -aG "$group" "$KRILL_USER" 2>/dev/null || true
        fi
    done

    # Add kiosk user to the krill group so it can read the API key
    if getent group krill >/dev/null 2>&1; then
        usermod -aG krill "$KRILL_USER" 2>/dev/null || true
    fi

    # Allow group read on the API key file so the kiosk user can access it
    if [ -f "$KRILL_API_KEY_FILE" ]; then
        chmod 0440 "$KRILL_API_KEY_FILE"
    fi

    # Ensure the credentials directory is group-traversable so the kiosk
    # user (in the krill group) can reach the API key file inside it.
    local CRED_DIR
    CRED_DIR=$(dirname "$KRILL_API_KEY_FILE")
    if [ -d "$CRED_DIR" ]; then
        chmod 0750 "$CRED_DIR"
    fi

    # Ensure .config directory exists for onboard and other kiosk settings
    local KIOSK_HOME
    KIOSK_HOME=$(getent passwd "$KRILL_USER" | cut -d: -f6)
    sudo -u "$KRILL_USER" mkdir -p "$KIOSK_HOME/.config"
}

setup_certificate_trust() {
    log_info "Setting up certificate trust..."

    if [ ! -f "$KRILL_CERT" ]; then
        log_error "Krill certificate not found at $KRILL_CERT"
        log_error "Please ensure the krill package is properly installed"
        exit 1
    fi

    # System-wide trust
    cp "$KRILL_CERT" /usr/local/share/ca-certificates/krill.crt
    update-ca-certificates

    # Chromium NSS database for the kiosk user
    local KIOSK_HOME
    KIOSK_HOME=$(getent passwd "$KRILL_USER" | cut -d: -f6)
    local NSS_DB="$KIOSK_HOME/.pki/nssdb"

    sudo -u "$KRILL_USER" mkdir -p "$NSS_DB"

    if [ ! -f "$NSS_DB/cert9.db" ]; then
        sudo -u "$KRILL_USER" certutil -d "sql:$NSS_DB" -N --empty-password
    fi

    sudo -u "$KRILL_USER" certutil -d "sql:$NSS_DB" -A -t "C,," \
        -n "Krill Server" -i "$KRILL_CERT" 2>/dev/null || true

    log_info "Certificate trust configured"
}

setup_chromium_policy() {
    log_info "Configuring Chromium policies..."

    mkdir -p "$CHROMIUM_POLICIES_DIR"

    cat > "$CHROMIUM_POLICIES_DIR/krill-kiosk.json" << EOF
{
    "AutoplayAllowed": true,
    "DefaultBrowserSettingEnabled": false,
    "BookmarkBarEnabled": false,
    "PasswordManagerEnabled": false,
    "TranslateEnabled": false,
    "MetricsReportingEnabled": false,
    "HardwareAccelerationModeEnabled": true,
    "BackgroundModeEnabled": false,
    "RestoreOnStartup": 4,
    "HomepageLocation": "$KRILL_URL",
    "RestoreOnStartupURLs": ["$KRILL_URL"]
}
EOF

    chmod 644 "$CHROMIUM_POLICIES_DIR/krill-kiosk.json"
    log_info "Chromium policies configured"
}

configure_onboard() {
    log_info "Configuring on-screen keyboard (onboard)..."

    local KIOSK_HOME
    KIOSK_HOME=$(getent passwd "$KRILL_USER" | cut -d: -f6)

    # Create dconf profile so the kiosk user picks up our defaults
    mkdir -p "$(dirname "$ONBOARD_DCONF_PROFILE")"
    cat > "$ONBOARD_DCONF_PROFILE" << 'EOF'
user-db:user
system-db:kiosk
EOF

    # Write dconf defaults for onboard
    mkdir -p "$ONBOARD_DCONF_DB_DIR"
    cat > "$ONBOARD_DCONF_DB_DIR/00-onboard" << 'EOF'
[org/onboard]
theme='/usr/share/onboard/themes/Droid.theme'
layout='/usr/share/onboard/layouts/Phone.onboard'
start-minimized=false
auto-show/enabled=true
auto-show/reposition-method-docking=true

[org/onboard/window]
docking-enabled=true
docking-edge='bottom'
force-to-top=true
transparent-background=false
enable-inactive-transparency=false

[org/onboard/window/landscape]
dock-height=300

[org/onboard/auto-show]
enabled=true

[org/onboard/typing-assistance]
auto-capitalization=true

[org/gnome/desktop/a11y]
always-show-universal-access-status=true

[org/gnome/desktop/a11y/applications]
screen-keyboard-enabled=true

[org/gnome/desktop/interface]
toolkit-accessibility=true
EOF

    dconf update

    # Create an XDG autostart entry so onboard starts with the X session
    mkdir -p "$ONBOARD_AUTOSTART_DIR"
    cat > "$ONBOARD_AUTOSTART_DIR/onboard-kiosk.desktop" << 'EOF'
[Desktop Entry]
Type=Application
Name=Onboard On-Screen Keyboard
Exec=onboard
AutostartCondition=GSettings org.gnome.desktop.a11y.applications screen-keyboard-enabled
X-GNOME-Autostart-Phase=Application
X-GNOME-AutoRestart=true
NoDisplay=true
EOF

    # Enable the accessibility keyboard via gsettings for the kiosk user
    cat > "$KIOSK_HOME/.config/onboard-autostart.sh" << 'SCRIPT'
#!/bin/bash
# Enable accessibility keyboard and start onboard
export DCONF_PROFILE=kiosk

# Enable on-screen keyboard in accessibility settings
gsettings set org.gnome.desktop.a11y.applications screen-keyboard-enabled true 2>/dev/null || true

# Start onboard if not already running
if ! pgrep -u "$(whoami)" -x onboard >/dev/null; then
    onboard &
fi
SCRIPT

    chmod +x "$KIOSK_HOME/.config/onboard-autostart.sh"
    chown -R "$KRILL_USER:$KRILL_USER" "$KIOSK_HOME/.config"

    log_info "On-screen keyboard configured (onboard, docked at bottom)"
}

configure_xorg() {
    log_info "Configuring Xorg..."

    # Allow the kiosk user to start X without root
    mkdir -p /etc/X11
    cat > /etc/X11/Xwrapper.config << 'EOF'
allowed_users=anybody
needs_root_rights=yes
EOF

    # The Pi 5 has two DRM cards: card0 (vc4 display controller) and
    # card1 (v3d 3D renderer). Xorg gets confused by multiple cards and
    # fails with "Cannot run in framebuffer mode". We must explicitly
    # point it at the vc4 display card.
    local DRM_CARD="/dev/dri/card0"

    # Auto-detect: find the card driven by vc4-drm (the display controller)
    for card in /sys/class/drm/card*; do
        local card_name
        card_name=$(basename "$card")
        if [ -f "$card/device/uevent" ] && grep -q "DRIVER=vc4-drm" "$card/device/uevent" 2>/dev/null; then
            DRM_CARD="/dev/dri/$card_name"
            log_info "Found VC4 display controller at $DRM_CARD"
            break
        fi
    done

    mkdir -p /etc/X11/xorg.conf.d
    cat > /etc/X11/xorg.conf.d/99-krill-kiosk.conf << EOF
# Krill Kiosk — tell Xorg which GPU card to use on Pi 5
Section "Device"
    Identifier "Pi5 Display"
    Driver     "modesetting"
    Option     "kmsdev" "$DRM_CARD"
EndSection
EOF

    log_info "Xorg configured for Pi 5 ($DRM_CARD)"
}

create_kiosk_script() {
    local KIOSK_HOME
    KIOSK_HOME=$(getent passwd "$KRILL_USER" | cut -d: -f6)
    local KIOSK_SCRIPT="$KIOSK_HOME/krill-kiosk.sh"

    log_info "Creating kiosk browser script..."

    cat > "$KIOSK_SCRIPT" << EOF
#!/bin/bash
# Krill Kiosk — launches Chromium fullscreen on X11 with on-screen keyboard

KRILL_API_KEY_FILE="$KRILL_API_KEY_FILE"
KRILL_PORT="$KRILL_PORT"

# Read API key at launch time (may have been rotated since install)
if [ -f "\$KRILL_API_KEY_FILE" ]; then
    KRILL_API_KEY=\$(cat "\$KRILL_API_KEY_FILE" | tr -d '[:space:]')
fi

if [ -z "\$KRILL_API_KEY" ]; then
    echo "WARNING: Could not read API key from \$KRILL_API_KEY_FILE"
    KRILL_URL="https://localhost:\$KRILL_PORT"
else
    KRILL_URL="https://localhost:\$KRILL_PORT?api_key=\$KRILL_API_KEY"
fi

# Wait for Krill server
echo "Waiting for Krill server..."
for i in {1..30}; do
    if curl -sk "https://localhost:\$KRILL_PORT" >/dev/null 2>&1; then
        echo "Krill server is ready"
        break
    fi
    sleep 1
done

# Disable screen blanking and power management
xset s off
xset s noblank
xset -dpms

# Use the kiosk dconf profile for onboard settings
export DCONF_PROFILE=kiosk

# Start accessibility bus (required for onboard auto-show)
if command -v /usr/libexec/at-spi-bus-launcher >/dev/null 2>&1; then
    /usr/libexec/at-spi-bus-launcher --launch-immediately &
elif command -v /usr/lib/at-spi-bus-launcher >/dev/null 2>&1; then
    /usr/lib/at-spi-bus-launcher --launch-immediately &
fi
sleep 1

# Enable GNOME accessibility before onboard starts so it never prompts.
# Write directly via dconf to avoid gsettings/dbus-session issues.
export GSETTINGS_BACKEND=dconf
dconf write /org/gnome/desktop/a11y/applications/screen-keyboard-enabled true 2>/dev/null || true
dconf write /org/gnome/desktop/interface/toolkit-accessibility true 2>/dev/null || true

# Launch on-screen keyboard (docked at bottom, auto-show on text focus)
onboard &
ONBOARD_PID=\$!

# Clean up onboard when Chromium exits
cleanup() {
    kill "\$ONBOARD_PID" 2>/dev/null
}
trap cleanup EXIT

exec chromium \
    --kiosk \
    --noerrdialogs \
    --disable-infobars \
    --disable-session-crashed-bubble \
    --disable-translate \
    --no-first-run \
    --fast \
    --fast-start \
    --disable-features=TranslateUI \
    --disable-pinch \
    --overscroll-history-navigation=0 \
    --disable-features=Translate \
    --autoplay-policy=no-user-gesture-required \
    --start-fullscreen \
    --start-maximized \
    --password-store=basic \
    --disable-component-update \
    --check-for-update-interval=31536000 \
    --enable-logging \
    --log-file=/tmp/chromium.log \
    --v=1 \
    "\$KRILL_URL"
EOF

    chmod +x "$KIOSK_SCRIPT"
    chown "$KRILL_USER:$KRILL_USER" "$KIOSK_SCRIPT"

    log_info "Kiosk script created at $KIOSK_SCRIPT"
}

create_launcher() {
    local KIOSK_HOME
    KIOSK_HOME=$(getent passwd "$KRILL_USER" | cut -d: -f6)
    local KIOSK_UID
    KIOSK_UID=$(id -u "$KRILL_USER")

    log_info "Creating launcher script..."

    cat > "$LAUNCHER_SCRIPT" << EOF
#!/bin/bash
# Krill Kiosk Launcher — starts X11 with no cursor and runs Chromium

KIOSK_USER="$KRILL_USER"
KIOSK_HOME="$KIOSK_HOME"
KIOSK_UID="$KIOSK_UID"

mkdir -p "/run/user/\$KIOSK_UID"
chown "\$KIOSK_USER:\$KIOSK_USER" "/run/user/\$KIOSK_UID"
chmod 700 "/run/user/\$KIOSK_UID"

chvt 7

# Start X11 with -nocursor to completely disable the mouse pointer.
# This is the only reliable way to hide the cursor — it prevents
# the X server from ever rendering one.
exec su - "\$KIOSK_USER" -c "
    export XDG_RUNTIME_DIR=/run/user/\$KIOSK_UID
    exec xinit \$KIOSK_HOME/krill-kiosk.sh -- :0 vt7 -nocursor -nolisten tcp
"
EOF

    chmod +x "$LAUNCHER_SCRIPT"

    log_info "Launcher created at $LAUNCHER_SCRIPT"
}

create_systemd_service() {
    log_info "Creating systemd service..."

    cat > "$KIOSK_SERVICE" << EOF
[Unit]
Description=Krill Kiosk (X11)
After=krill.service
Wants=krill.service
ConditionPathExists=/dev/dri/card0

[Service]
Type=simple
ExecStartPre=/bin/sleep 3
ExecStart=$LAUNCHER_SCRIPT

TTYPath=/dev/tty7
TTYReset=yes
TTYVHangup=yes
TTYVTDisallocate=yes

Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

    systemctl daemon-reload
    log_info "Systemd service created"
}

configure_gpu() {
    log_info "Configuring GPU..."

    local CONFIG_FILE=""
    if [ -f /boot/firmware/config.txt ]; then
        CONFIG_FILE="/boot/firmware/config.txt"
    elif [ -f /boot/config.txt ]; then
        CONFIG_FILE="/boot/config.txt"
    else
        log_warn "Could not find config.txt, skipping GPU configuration"
        return
    fi

    if ! grep -q "^gpu_mem=" "$CONFIG_FILE"; then
        echo "gpu_mem=128" >> "$CONFIG_FILE"
        log_info "Set GPU memory to 128MB"
    fi

    if ! grep -q "^dtoverlay=vc4-kms-v3d" "$CONFIG_FILE"; then
        echo "dtoverlay=vc4-kms-v3d" >> "$CONFIG_FILE"
        log_info "Enabled VC4 KMS driver"
    fi
}

print_status() {
    echo ""
    echo "=============================================="
    echo "🦐 Krill Kiosk Installation Complete!"
    echo "=============================================="
    echo ""
    echo "Configuration:"
    echo "  • Kiosk user: $KRILL_USER"
    echo "  • Display:    X11 with cursor disabled (-nocursor)"
    echo "  • Browser:    Chromium in fullscreen kiosk mode"
    echo "  • Keyboard:   Onboard (auto-show, docked at bottom)"
    echo "  • URL:        $KRILL_URL"
    echo ""
    echo "Service management:"
    echo "  • Start:   sudo systemctl start krill-kiosk"
    echo "  • Stop:    sudo systemctl stop krill-kiosk"
    echo "  • Disable: sudo systemctl disable krill-kiosk"
    echo "  • Logs:    journalctl -u krill-kiosk -f"
    echo ""
    echo "To start now:  sudo systemctl start krill-kiosk"
    echo "Or reboot:     sudo reboot"
    echo ""
}

uninstall() {
    log_info "Uninstalling Krill Kiosk..."

    systemctl stop krill-kiosk.service 2>/dev/null || true
    systemctl disable krill-kiosk.service 2>/dev/null || true

    rm -f "$KIOSK_SERVICE"
    rm -f "$LAUNCHER_SCRIPT"
    rm -f "$CHROMIUM_POLICIES_DIR/krill-kiosk.json"
    rm -f /etc/X11/Xwrapper.config
    rm -f /etc/X11/xorg.conf.d/99-krill-kiosk.conf
    rm -f /usr/local/share/ca-certificates/krill.crt
    rm -f "$ONBOARD_AUTOSTART_DIR/onboard-kiosk.desktop"
    rm -f "$ONBOARD_DCONF_PROFILE"
    rm -rf "$ONBOARD_DCONF_DB_DIR"
    dconf update 2>/dev/null || true
    update-ca-certificates

    systemctl daemon-reload

    log_info "Kiosk configuration removed"
    log_info "Note: User '$KRILL_USER' and packages were not removed"
    log_info "To remove user:    sudo userdel -r $KRILL_USER"
    log_info "To remove packages: sudo apt remove chromium xserver-xorg xinit onboard dconf-cli at-spi2-core"
}

main() {
    check_root

    if [ "$1" = "--uninstall" ] || [ "$1" = "-u" ]; then
        uninstall
        exit 0
    fi

    if [ "$1" = "--help" ] || [ "$1" = "-h" ]; then
        echo "Usage: $0 [--uninstall]"
        echo ""
        echo "Installs a fullscreen kiosk on Raspberry Pi 5 with HDMI touchscreen."
        echo "The mouse cursor is disabled — this is designed for touch-only use."
        echo ""
        echo "Options:"
        echo "  --uninstall, -u  Remove kiosk configuration"
        echo "  --help, -h       Show this help message"
        echo ""
        echo "Environment variables:"
        echo "  KRILL_KIOSK_USER  Username for the kiosk (default: kiosk)"
        exit 0
    fi

    echo ""
    echo "=============================================="
    echo "🦐 Krill Kiosk Installer (Pi 5 + HDMI Touch)"
    echo "=============================================="
    echo ""

    prompt_tos_acceptance

    check_raspberry_pi_5
    check_no_desktop
    check_hdmi
    check_krill_running
    read_api_key
    disable_wayland
    install_packages
    create_kiosk_user
    setup_certificate_trust
    setup_chromium_policy
    configure_onboard
    configure_xorg
    create_kiosk_script
    create_launcher
    create_systemd_service
    configure_gpu

    systemctl enable krill-kiosk.service

    print_status
}

main "$@"
