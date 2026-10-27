#!/usr/bin/env bash

# Usage: patch-deb-wmclass.sh <built.deb>
#
# jpackage generates the deb's .desktop without a StartupWMClass, so GNOME never
# matches the running window against the launcher: the app shows the right icon in
# the app grid but a generic gear in the dock/taskbar while it runs, with the
# window marked <untracked> and the dock's click launching a duplicate. The
# window's own X11 WM_CLASS, as AWT exposes it (dots and slashes mangled to
# dashes, verified against GNOME's Looking Glass and with a per-user override on
# Ubuntu), is com-music-bitchord-desktop-MainKt — the same claim the AppImage's
# desktop file already makes (see appimage.sh).
set -euo pipefail

deb=$1
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

dpkg-deb -R "$deb" "$work/root"
desktop=$(find "$work/root/usr/share/applications" -name '*.desktop' -print -quit)
if [ -z "$desktop" ]; then
  echo "no .desktop in the deb; nothing to claim the running window with" >&2
  exit 1
fi
if grep -q '^StartupWMClass=' "$desktop"; then
  echo "$(basename "$deb"): StartupWMClass already set"
  exit 0
fi
printf 'StartupWMClass=com-music-bitchord-desktop-MainKt\n' >> "$desktop"
dpkg-deb -b --root-owner-group "$work/root" "$deb"
echo "$(basename "$deb"): $(basename "$desktop") now claims com-music-bitchord-desktop-MainKt"
