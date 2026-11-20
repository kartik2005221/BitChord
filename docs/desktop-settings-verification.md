# Desktop settings verification

Automated results are recorded below after the final combined run. Physical
trackpad and OS integration checks still require the target desktop environment.

## Manual acceptance checks

### Black canvas and distinct elements

Palette anchors are orange `#FF6500`, blue `#1E3E62`, navy `#0B192C`, and black
`#000000`. Light mode uses related pale-blue surfaces and navy text, with a
darker orange for small navigation marks where the bright orange lacks contrast.
See `desktop-accent-inventory.md` for intentional non-theme colors.

1. Upgrade with an existing Dark selection and pitch black previously off.
   Verify the main page background is `#000000`, while sidebar, bottom player,
   raised cards, and selected controls are distinguishable.
2. Opt out of pitch black, restart, and verify the classic-gray choice persists.
3. Select Light; verify readable Light colors. Select System and change OS
   appearance; verify the saved black preference affects dark appearance only.
4. Open Settings, dialogs, search, full player, lyrics, and Replay. Check text,
   focus indicators, hover/selection contrast, and artwork preservation.

### GNOME Ubuntu and GNOME Fedora navigation

1. Visit Home → Search → a collection. Swipe horizontally/left rapidly and
   repeatedly on the trackpad. BitChord must not go back through its history.
2. Verify horizontal carousels and vertical pages still scroll normally.
3. Verify the visible Back button and Alt+Left navigate one step deliberately.
4. Physical mouse side-button Back is also disabled: the removed generic event
   path cannot reliably distinguish it from an emulated trackpad Back event.
5. In Settings open every category and linked destination, especially Listen
   Together, integrations, graphics, sources, local folders, and updates. Verify
   a visible route back to the correct Settings parent and matching Alt+Left.
6. Verify ordinary full-player gestures and queue/row actions remain unchanged;
   the reported issue was previous-screen navigation, not song gestures.

### Paused session

1. Play song A in a queue, seek midway, pause, then quit normally. Reopen:
   navigation starts fresh, A appears paused, queue and playhead are restored.
2. Repeat while A is playing at exit. Reopen: no autoplay, source resolution,
   decoder opening, or audio output until Play. Press Play and verify position.
3. Seek while the restored player is paused, then press Play. Verify the new
   position is used. Test next/previous and a queue over 200 entries.
   With a deliberately slow source, also Pause/toggle and seek while first Play
   is still resolving. Cancellation must remain paused; loading must not undo
   a newer seek. Decoder failure/source fallback must retain the playhead.
4. Disable Restore last session, restart, and verify the player does not restore.
5. Verify party playback cannot replace the personal saved session.
6. Remove a downloaded file after exit; launching must still show paused metadata
   without silently clearing the queue. Normal playback handles availability
   only when explicitly started.
7. Force-stop after playback progress: restoration may lag by one checkpoint
   interval. Normal clean exit should save the latest sampled position.

### Downloads preferences

1. Choose each opening tab, leave Downloads, and reopen it. Test Last used and
   restart persistence. A fresh preference uses Playlists.
2. With automatic updates off, open a changed downloaded playlist; no automatic
   download should occur and manual Update must remain available.
3. Enable update-on-open. Open a playlist with additions over several pages;
   update only after the complete listing is available. Existing downloaded
   files must not be deleted when the remote playlist removes tracks.
4. Simulate a listing/network failure and navigation away during loading; no
   partial snapshot should replace a complete saved playlist. Reopening retries
   normally; ordinary recomposition must not loop downloads.
5. Open download folder; verify the existing folder opens without relocating
   downloads. Unsupported desktop actions should report an error, not crash.

### Artwork privacy

1. Verify master switch, strength, and scope preferences persist across restart.
   Distinguish artwork privacy from backdrop/glass blur performance controls.
2. Inspect browsing album/artist artwork, headers/banners, queue, full and bottom
   player, PiP, and video/motion thumbnails with privacy enabled per scope.
3. Temporary reveal must be explicit, time-limited, nonpersistent, and never
   expose external OS/Discord artwork. Verify hide-again behavior too.
4. Verify OS media controls and Discord omit artwork when auxiliary privacy is
   enabled, including an artwork request already in flight when toggled.
5. Text metadata is intentionally unchanged; artwork privacy is not anonymous
   listening or protection of track/artist names.

## Automated verification

- Local shared JVM suite: 78 tests, no failures/errors/skips.
- Local shared-UI JVM suite: 42 tests, no failures/errors/skips.
- Desktop verification is **not yet green**. A missing explicit Material color
  scheme return type was corrected. The subsequent desktop compiler daemon
  crashed; Gradle attempted an in-process fallback, and the run was stopped
  rather than exhausting this 1.2 GiB host (about 143 MiB available, 1.9 GiB swap
  used at diagnosis). This is an incomplete local run, not passing desktop tests
  and not evidence of a test assertion failure.
- Full Linux/Windows/Android and packaging verification will run on the feature
  branch's GitHub CI before merge. Results and links will be recorded here.
- `git diff --check` passes for the implementation before CI submission.
