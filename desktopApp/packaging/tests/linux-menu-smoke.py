#!/usr/bin/env python3
"""Native Linux menu smoke for the packaged launcher or AppImage. Run with xvfb-run -s '-screen 0 1280x800x24'."""

import argparse
import ctypes
import json
import os
from pathlib import Path
import subprocess
import signal
import time

from PIL import ImageChops, ImageDraw, ImageGrab, ImageStat, Image


SCREEN_SIZE = (1280, 800)
MAGENTA = (221, 32, 100)
COVER = (410, 140)
HEADER_MORE = (847, 239)
HEADER_MENU_PROBE = (870, 263, 1010, 344)
APP_BACK = (62, 91)
DOWNLOADS = (120, 312)


class NativeInput:
    def __init__(self):
        self.xlib = ctypes.CDLL("libX11.so.6")
        self.xtst = ctypes.CDLL("libXtst.so.6")
        display = ctypes.c_void_p
        self.xlib.XOpenDisplay.restype = display
        self.xlib.XFlush.argtypes = [display]
        self.xlib.XSync.argtypes = [display, ctypes.c_int]
        self.xlib.XKeysymToKeycode.argtypes = [display, ctypes.c_ulong]
        self.xtst.XTestFakeMotionEvent.argtypes = [
            display, ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_ulong]
        self.xtst.XTestFakeButtonEvent.argtypes = [
            display, ctypes.c_uint, ctypes.c_int, ctypes.c_ulong]
        self.xtst.XTestFakeKeyEvent.argtypes = [
            display, ctypes.c_uint, ctypes.c_int, ctypes.c_ulong]
        self.display = self.xlib.XOpenDisplay(None)
        assert self.display, "A private X11 display is required"

    def click(self, point):
        self.xtst.XTestFakeMotionEvent(self.display, -1, *point, 0)
        self.xlib.XSync(self.display, 0)
        # Let Compose process hover before clicking a row action.
        time.sleep(0.08)
        self.xtst.XTestFakeButtonEvent(self.display, 1, 1, 0)
        self.xlib.XSync(self.display, 0)
        time.sleep(0.04)
        self.xtst.XTestFakeButtonEvent(self.display, 1, 0, 0)
        self.xlib.XSync(self.display, 0)

    def escape(self):
        keycode = self.xlib.XKeysymToKeycode(self.display, 0xFF1B)
        self.xtst.XTestFakeKeyEvent(self.display, keycode, 1, 0)
        self.xtst.XTestFakeKeyEvent(self.display, keycode, 0, 0)
        self.xlib.XFlush(self.display)


def screen():
    shot = ImageGrab.grab()
    assert shot.size == SCREEN_SIZE, f"Expected {SCREEN_SIZE}, found {shot.size}"
    return shot


def wait_for(predicate, timeout, detail):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        shot = screen()
        if predicate(shot):
            return shot
        time.sleep(0.08)
    raise AssertionError(detail)


def color_distance(left, right):
    return sum(abs(a - b) for a, b in zip(left, right))


def cover_loaded(shot):
    return color_distance(shot.getpixel(COVER), MAGENTA) <= 15


def fixture_artwork_loaded_in_downloads(shot):
    return color_distance(shot.getpixel((300, 213)), MAGENTA) <= 15


def first_row_menu_anchor(shot):
    # Provider responses can change the header's height. The first song is
    # laid out beside its square fixture cover below the collection artwork.
    matching_y = []
    for y in range(325, 500):
        if any(color_distance(shot.getpixel((x, y)), MAGENTA) <= 15 for x in range(305, 365, 4)):
            if matching_y and y > matching_y[-1] + 1:
                break
            matching_y.append(y)
    if not matching_y:
        return None
    return (1209, (matching_y[0] + matching_y[-1]) // 2)


def menu_pixels_changed(before, now, probe):
    diff = ImageChops.difference(before.crop(probe), now.crop(probe))
    return sum(ImageStat.Stat(diff).mean) >= 10


def exercise_menu(native, label, button, probe, output):
    before = screen()
    before.save(output / f"{label}-before.png")
    native.click(button)
    visible = lambda shot: menu_pixels_changed(before, shot, probe)
    first = wait_for(visible, 5, "The menu never rendered")
    first.save(output / f"{label}-open.png")
    started = time.monotonic()
    end = started + 2
    elapsed = 0.0
    while elapsed < 2:
        shot = screen()
        elapsed = time.monotonic() - started
        if not visible(shot) or not cover_loaded(shot):
            shot.save(output / f"{label}-failed.png")
            return {
                "menu_visible_at_failure": visible(shot),
                "artwork_visible_at_failure": cover_loaded(shot),
                "seconds_since_first_visible_frame": round(elapsed, 3),
                "passed": False,
            }
        time.sleep(min(0.06, max(0, end - time.monotonic())))
    shot.save(output / f"{label}-two-seconds.png")
    native.escape()
    wait_for(lambda shot: cover_loaded(shot) and not visible(shot), 3, "Escape did not dismiss the menu")
    native.click(button)
    wait_for(visible, 3, "Menu could not be reopened")
    native.click((573, 580))
    wait_for(lambda shot: cover_loaded(shot) and not visible(shot), 3, "Outside click did not dismiss the menu")
    return {"passed": True, "seconds_visible": round(elapsed, 3)}


def seed_preferences(seed, output, launcher):
    artwork = Image.new("RGB", (280, 280), (69, 187, 187))
    ImageDraw.Draw(artwork).ellipse((42, 42, 238, 238), fill=MAGENTA)
    artwork.save(output / "artwork.png")
    for name in ("prefs", "home", "cache", "config"):
        (output / name).mkdir()
    subprocess.run(
        ["java", f"-Djava.util.prefs.userRoot={output / 'prefs'}",
         str(seed), str(output / "artwork.png"), str(launcher)],
        check=True,
    )
    return {
        **os.environ,
        "APPIMAGE_EXTRACT_AND_RUN": "1",
        "JAVA_TOOL_OPTIONS": f"-Djava.util.prefs.userRoot={output / 'prefs'} -Duser.home={output / 'home'}",
        "XDG_CACHE_HOME": str(output / "cache"),
        "XDG_CONFIG_HOME": str(output / "config"),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("launcher", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    launcher = args.launcher.resolve()
    env = seed_preferences(Path(__file__).with_name("MenuSmokeFixture.java"), output, launcher)
    native = NativeInput()
    results = {}
    with (output / "app.log").open("w") as log:
        process = subprocess.Popen([str(launcher)], cwd=output, env=env, stdout=log, stderr=log, start_new_session=True)
        try:
            wait_for(lambda shot: min(shot.getpixel((320, 360))) >= 200, 45,
                     "BitChord did not start with the prepared fixture profile")
            # Wait for the first render to receive pointer events and bring up
            # Downloads. Repeating the navigation is harmless before it opens.
            def open_downloads(shot):
                if fixture_artwork_loaded_in_downloads(shot):
                    return True
                native.click(DOWNLOADS)
                time.sleep(0.7)
                return False
            wait_for(open_downloads, 15, "Downloads did not open")
            wait_for(fixture_artwork_loaded_in_downloads, 15, "The three fixture collections were not shown in Downloads")
            for name, y, menus in (
                ("youtube", 348, (("header", HEADER_MORE, HEADER_MENU_PROBE), ("song-row", None, None))),
                ("album", 205, (("header", HEADER_MORE, HEADER_MENU_PROBE),)),
                ("spotify", 277, (("header", HEADER_MORE, HEADER_MENU_PROBE),)),
            ):
                native.click((420, y))
                wait_for(cover_loaded, 15, f"The {name} detail page never loaded")
                for suffix, button, probe in menus:
                    label = f"{name}-{suffix}"
                    try:
                        if button is None:
                            painted = wait_for(first_row_menu_anchor, 10, "First song artwork did not render")
                            button = first_row_menu_anchor(painted)
                            y = button[1]
                            probe = (953, y + 13, 1050, y + 97)
                        results[label] = exercise_menu(native, label, button, probe, output)
                    except AssertionError as failure:
                        results[label] = {"passed": False, "error": str(failure)}
                        screen().save(output / f"{label}-failed.png")
                    native.escape()
                    wait_for(cover_loaded, 5, f"The {name} artwork did not recover after closing the menu")
                    time.sleep(0.25)
                native.click(APP_BACK)
                wait_for(fixture_artwork_loaded_in_downloads, 15, "Back did not return to the three Downloads fixtures")
        except BaseException:
            screen().save(output / "harness-failed.png")
            raise
        finally:
            (output / "results.json").write_text(json.dumps(results, indent=2) + "\n")
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
    print(json.dumps(results, indent=2))
    return 0 if len(results) == 4 and all(result["passed"] for result in results.values()) else 1


if __name__ == "__main__":
    raise SystemExit(main())
