#!/usr/bin/env python3
"""
krill_auth — derive the Krill cluster Bearer token inside a Lambda script.

Krill secures its API with a single 4-digit cluster PIN shared by every server
and app. The server stores two files under /etc/krill/credentials:

  pin_derived_key   the Bearer token itself:
                    HMAC-SHA256(key="krill-api-pbkdf2-v1", msg=PIN) as lowercase hex
  pin_hash          "<salt>:<base64(HMAC-SHA256(key=salt, msg=PIN))>"  (PIN check only)

Lambdas run as the `krill` user on the same host, so the simplest and most
robust path is to read the already-derived token straight from disk — that is
exactly the value the server compares against (server PinProvider.bearerToken).

Wire contract (must stay byte-identical to `krill-reset-pin` and the server):
    derive: HMAC-SHA256(key=b"krill-api-pbkdf2-v1", msg=pin).hexdigest()

Usage:
    import requests, krill_auth

    # Easiest — read the token from disk and attach it:
    r = requests.get("https://localhost:8442/health",
                     auth=krill_auth.KrillPinAuth(),
                     verify="/etc/krill/certs/krill.crt")

    # Or build the header yourself:
    r = requests.get(url, headers=krill_auth.auth_header(), verify=cert)

    # Or derive from a raw PIN you already hold (e.g. tests):
    token = krill_auth.derive_bearer_token("1234")
"""
import base64
import hashlib
import hmac
from pathlib import Path

CREDENTIALS_DIR = "/etc/krill/credentials"
DERIVED_KEY_PATH = f"{CREDENTIALS_DIR}/pin_derived_key"
PIN_HASH_PATH = f"{CREDENTIALS_DIR}/pin_hash"
KRILL_CERT = "/etc/krill/certs/krill.crt"
# Fixed HMAC key for token derivation. Shared verbatim with krill-reset-pin and
# the server's PinProvider — do NOT change or auth breaks cluster-wide.
_DERIVE_HMAC_KEY = b"krill-api-pbkdf2-v1"


class KrillPinError(RuntimeError):
    """No PIN configured, or a credentials file is missing/empty/unreadable."""


def derive_bearer_token(pin: str) -> str:
    """Compute the Bearer token from a raw 4-digit PIN.

    Byte-identical to `krill-reset-pin`:
        openssl dgst -sha256 -hmac "krill-api-pbkdf2-v1" -hex
    Returns lowercase hex.
    """
    return hmac.new(_DERIVE_HMAC_KEY, pin.encode(), hashlib.sha256).hexdigest()


def bearer_token(path: str = DERIVED_KEY_PATH) -> str:
    """Read the precomputed Bearer token from /etc/krill/credentials/pin_derived_key.

    This is what the server validates against. Raises KrillPinError if the file
    is missing or empty (no PIN set — run `sudo krill-reset-pin`).
    """
    try:
        token = Path(path).read_text().strip()
    except OSError as e:
        raise KrillPinError(
            f"Cannot read {path}: {e}. Lambdas must run as the 'krill' user, "
            f"and a PIN must be set with: sudo krill-reset-pin"
        ) from e
    if not token:
        raise KrillPinError(f"{path} is empty — set a PIN with: sudo krill-reset-pin")
    return token


def is_configured(path: str = DERIVED_KEY_PATH) -> bool:
    """True if a non-empty derived key exists (a PIN is configured)."""
    try:
        return bool(Path(path).read_text().strip())
    except OSError:
        return False


def auth_header(token: str | None = None) -> dict:
    """Return {"Authorization": "Bearer <token>"}, reading the token if not given."""
    return {"Authorization": f"Bearer {token or bearer_token()}"}


def verify_pin(pin: str, path: str = PIN_HASH_PATH) -> bool:
    """Check a candidate PIN against /etc/krill/credentials/pin_hash.

    pin_hash format: "<salt_hex>:<base64(HMAC-SHA256(key=salt_hex, msg=pin))>".
    Constant-time comparison. Not needed for auth — handy for sanity checks.
    """
    try:
        raw = Path(path).read_text().strip()
    except OSError as e:
        raise KrillPinError(f"Cannot read {path}: {e}") from e
    salt, _, expected = raw.partition(":")
    if not expected:
        raise KrillPinError(f"{path} is malformed (expected 'salt:hash')")
    actual = base64.b64encode(
        hmac.new(salt.encode(), pin.encode(), hashlib.sha256).digest()
    ).decode()
    return hmac.compare_digest(actual, expected)


# Optional requests integration — only defined if `requests` is importable, so
# the stdlib-only helpers above keep working without the dependency.
try:
    import requests

    class KrillPinAuth(requests.auth.AuthBase):
        """requests auth that attaches the Krill Bearer token.

        Usage: requests.get(url, auth=KrillPinAuth(), verify=cert)
        Pass an explicit `token` to skip the disk read.
        """

        def __init__(self, token: str | None = None, path: str = DERIVED_KEY_PATH):
            self._token = token
            self._path = path

        def __call__(self, request):
            token = self._token or bearer_token(self._path)
            request.headers["Authorization"] = f"Bearer {token}"
            return request

except ImportError:  # pragma: no cover - requests is a declared dependency
    pass


if __name__ == "__main__":
    # Quick self-check: print the token the server expects (run as krill user).
    print(bearer_token())
