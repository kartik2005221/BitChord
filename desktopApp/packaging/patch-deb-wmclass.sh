#!/usr/bin/env bash

# Usage: patch-deb-wmclass.sh <built.deb>
#
# The deb ships no launcher the desktop can match a running window against: its
# /usr/share/applications holds no .desktop (out of compose 1.10.3's packageDeb),
# so the app grid has no entry, the dock marks the window <untracked>, shows a
# generic icon, and a dock click spawns a duplicate. This gives the deb the same
# launcher the AppImage already claims (see appimage.sh): StartupWMClass=
# com-music-bitchord-desktop-MainKt — the window's real X11 WM_CLASS, as AWT
# exposes it (dots and slashes mangled to dashes; verified against GNOME's
# Looking Glass and with a per-user override on Ubuntu). Should a deb carry a
# .desktop after all (other compose/jpackage layouts), that one is finished
# instead. The script talks while it works: every stage prints its state, so a
# CI log shows exactly where a deb refuses to carry the entry.
set -euo pipefail

deb=$1
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
wmclass=com-music-bitchord-desktop-MainKt

echo "patch: $(basename "$deb") — $(dpkg-deb --version 2>/dev/null | head -n1)"
dpkg-deb -R "$deb" "$work/root"

desktop=$(find "$work/root" -type f -name '*.desktop' -print -quit 2>/dev/null || true)
echo "patch: existing .desktop — ${desktop:-(none)}"

if [ -z "$desktop" ]; then
  # Synthesize the launcher: Exec points at the payload's own launcher, the icon
  # at whatever png the payload carries — paths as they land on an installed
  # machine, i.e. the unpack prefix $work/root is stripped.
  launcher=$(find "$work/root/opt" -type f -path '*/bin/*' -perm /111 -print -quit 2>/dev/null || true)
  [ -n "$launcher" ] || {
    echo "$(basename "$deb"): no .desktop and no launcher under /opt to write one for" >&2
    exit 1
  }
  icon=$(find "$work/root/opt" -type f -name '*.png' -print -quit 2>/dev/null || true)
  echo "patch: launcher — ${launcher#"$work/root"}; icon — ${icon:-(none)}"
  launcher=${launcher#"$work/root"}
  icon=${icon#"$work/root"}
  mkdir -p "$work/root/usr/share/applications"
  desktop="$work/root/usr/share/applications/bitchord.desktop"
  {
    echo "[Desktop Entry]"
    echo "Type=Application"
    echo "Name=BitChord"
    echo "GenericName=Music Player"
    echo "Comment=Aesthetic YouTube Music client"
    echo "Exec=$launcher"
    echo "Icon=${icon:-bitchord}"
    echo "Categories=AudioVideo;Audio;Player;"
    echo "Terminal=false"
    echo "StartupWMClass=$wmclass"
  } > "$desktop"
  echo "patch: the written entry:"
  cat "$desktop"
  kind=synthesized
else
  if grep -q "^StartupWMClass=$wmclass" "$desktop"; then
    echo "$(basename "$deb"): $(basename "$desktop") already claims $wmclass"
    exit 0
  fi
  printf 'StartupWMClass=%s\n' "$wmclass" >> "$desktop"
  echo "patch: appended — $(tail -n1 "$desktop")"
  kind=patched
fi

dpkg-deb -b --root-owner-group "$work/root" "$deb"
echo "patch: repacked"

# The claim must survive the repack — read it back out of the deb itself.
# First the deb's own contents table (naming-agnostic reporting), then the
# extracted tree is the enforced proof.
dpkg-deb -c "$deb" | grep -F 'usr/share/applications' \
  || echo "patch: NOTE — no applications dir in the deb's contents table; table has $(dpkg-deb -c "$deb" | wc -l) rows"

mkdir -p "$work/now"
dpkg-deb -x "$deb" "$work/now"
entry=$(find "$work/now" -type f -name '*.desktop' -print -quit 2>/dev/null || true)
echo "patch: entry after extract — ${entry:-(none)}"
if [ -z "$entry" ]; then
  echo "$(basename "$deb"): the claim did not survive the repack" >&2
  exit 1
fi
grep -H "^StartupWMClass=$wmclass" "$entry" || {
  echo "$(basename "$deb"): the claim did not survive the repack" >&2
  exit 1
}
echo "$(basename "$deb"): $kind $(basename "$entry") claiming $wmclass"
