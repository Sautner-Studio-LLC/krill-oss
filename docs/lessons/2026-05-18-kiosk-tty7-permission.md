# kiosk launcher missing chown for /dev/tty7 — X11 startup fails with Permission denied

**Issue:** [krill-oss#90](https://github.com/Sautner-Studio-LLC/krill-oss/issues/90)
**Upstream fix:** [krill#302](https://github.com/Sautner-Studio-LLC/krill/issues/302)
**Root cause category:** Documentation gap — missing prerequisite step in kiosk launcher
**Module:** blog / krill server package (private repo)

## What happened

A user following the Raspberry Pi Kiosk Tutorial at `krillswarm.com/posts/2026/04/14/raspberry-pi-kiosk-tutorial/` hit the following error in `journalctl -u kiosk` and couldn't get X11 to start:

```
xf86OpenConsole: Cannot open virtual console 7 (Permission denied)
```

Step 2 of the tutorial creates the kiosk user with `useradd ... -G video,audio,input,render,tty kiosk`. Step 6's kiosk-launcher calls `chvt 7` (as root) and then `su - kiosk -c "xinit ... vt7 ..."`. Xorg opens `/dev/tty7` with `O_RDWR` as the kiosk user, but on stock Raspberry Pi OS Lite the device is owned by `root:tty` with mode `crw--w----` — the `tty` group has **write-only** access, not read-write. Membership in the `tty` group is not enough; Xorg needs to be able to read the device too. The fix (confirmed by the reporter) is to `chown kiosk /dev/tty7` in the launcher **before** `chvt 7`, while the launcher is still running as root.

The same gap exists in `server/package/usr/local/bin/krill_kiosk_install.sh`'s `create_launcher()` function in the private krill repo. Both the blog post and the installer were filed as [krill#302](https://github.com/Sautner-Studio-LLC/krill/issues/302) for the upstream fix.

## Fix

- **`docs/_posts/2026-04-14-raspberry-pi-kiosk-tutorial.md`** (private krill repo, Step 6 heredoc): add `chown "$KIOSK_USER" /dev/tty7` before `chvt 7`. Also add a troubleshooting table row: `xf86OpenConsole: Cannot open virtual console 7 (Permission denied)` → add `chown kiosk /dev/tty7` before `chvt 7` in the launcher.
- **`server/package/usr/local/bin/krill_kiosk_install.sh`** (private krill repo, `create_launcher()` function): same `chown` line before `chvt 7` in the generated launcher heredoc.
- Tracked upstream in [krill#302](https://github.com/Sautner-Studio-LLC/krill/issues/302).

## Prevention

- When a launcher script uses `chvt N` and then hands off to a non-root user to start an X server on `vtN`, that user must **own** `/dev/ttyN` — group membership alone is not enough because Xorg opens the tty `O_RDWR` and the `tty` group on Pi OS Lite is write-only. Add `chown <user> /dev/ttyN` to the launcher template before `chvt N` and document it in the troubleshooting table.
- Troubleshooting tables in tutorials should cover X11 startup failures with the exact `journalctl` error string so users can self-serve without opening an issue.
- When a blog post tutorial and an automated installer script share the same logic, keep them in sync — a fix to one must be applied to the other in the same PR.
