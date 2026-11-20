# Desktop black surfaces, safe navigation, and session settings

## Confirmed requirements

- Use the Color Hunt palette `#FF6500`, `#1E3E62`, `#0B192C`, `#000000`:
  the default dark canvas is black, sidebar and chrome are navy, controls and
  selected surfaces are blue, and accents are orange. Players and cards remain
  distinct from the canvas. Classic dark uses the same family with a navy canvas.
- Light uses pale blue and off-white surfaces, navy text, and orange primary
  controls with black labels. Small navigation text/icons use a darker orange to
  retain contrast on light surfaces. Artwork and destructive colors stay distinct.
- Make black the default dark appearance, including existing installations, with
  a one-time migration. Preserve Light and System theme selection and allow a
  subsequent explicit choice of classic dark.
- Disable pointer/swipe Back navigation. Keep the visible Back button, Alt+Left,
  and normal scrolling. This request concerns previous-screen navigation, not
  track-row queue gestures or artwork track skipping.
- Reopening starts a fresh navigation session but restores the personal song,
  queue, and playhead paused. Never autoplay or fetch/decode audio merely to show
  the restored player.
- Audit recent features against desktop settings, expose meaningful missing
  controls, document deliberate exclusions, and test persistence and behavior.
- Append progress and verification to `~/BitChord-MEMORY.md`; never rewrite its
  existing content. Do not modify frozen historical release notes.

## Findings

1. `DesktopTheme.kt` contains separate classic dark and partial pitch-black
   palettes; the existing preference does not blacken every requested surface.
2. `DesktopApp.kt` handles `isBackPressed` pointer events directly. No dedicated
   desktop swipe recognizer was found. GNOME may translate a gesture to this
   mouse-style event; removing the handler removes this app-level Back path,
   without claiming to change GNOME's global gestures.
3. `DesktopPersistence` saves a bounded queue/index using serial coalesced
   asynchronous writes, but no playhead. Startup restores only UI queue state,
   not the engine selection. Android's last-played state is a useful reference.
4. `DesktopPlaybackEngine.load()` resolves and opens media even when paused;
   startup must therefore use a separate metadata-only restore path.

## Implementation sequence

### A. Theme and navigation

1. **Implemented:** black palette keeps the main canvas, content surface, lyrics,
   and Replay surface pure black, while sidebar, chrome, search, and top/bottom
   players use controlled navy and blue shades. Cards, selected controls, borders,
   accents, and Light/classic palettes remain distinct.
2. **Implemented:** versioned migration v1 sets `desktop_pitch_black=true` once
   without changing theme mode; a later explicit false remains the classic-dark
   opt-out. The preference now applies to effective dark System as well as Dark.
3. **Implemented:** pitch-black chrome/player shells bypass haze, noise, and
   dissolve masks to preserve their chosen opaque navy shades; artwork
   inside and control/card colors are retained. Appearance setting is available
   in System and describes scope.
4. **Implemented:** removed only the generic `isBackPressed` pointer handler and
   imports; Alt+Left and in-app Back routes remain. No scrolling/player/row
   gestures were changed.
5. **Implemented:** isolated palette/migration policy tests added/updated in
    `DesktopThemeTest.kt`. Gradle verification intentionally not run while the
    baseline is running; recommended command remains the JVM suite below.
6. **Palette follow-up:** tests assert the four dark anchors, light and dark
   contrast for primary labels and navigation, and wallpaper-tinted sidebar
   bounds. Final coordinated Gradle verification and visual review remain pending.

### B. Paused personal session restoration

1. Define a validated resume snapshot with bounded queue, selected item/index,
   and nonnegative duration-clamped position. Couple scalar position to song
   identity so stale progress cannot be applied to a different item.
2. Preserve asynchronous queue writes; checkpoint small scalar state without
   serializing the whole queue on each progress tick. Serialize queue and
   scalar updates safely so coalescing cannot drop one or overwrite newer data.
3. Add engine metadata-only restoration. Publish song/duration/position with
   `isPlaying=false`, `isLoading=false`; defer stream resolution, decoder,
   sink activity, and failures until explicit Play.
4. Ensure Play/toggle/seek/next work from restored state. Cancel or invalidate
   obsolete engine work. Respect volume and current playback configuration.
   Preserve the latest Pause/toggle/seek intent while the first source is still
   resolving/opening, and retain the requested playhead through decoder failures
   and source fallback instead of silently retrying at zero.
5. Wire startup restoration once, before ordinary playback observers can
   overwrite the snapshot with empty/default state. Do not restore navigation,
   temporary party queues, or previous playing status.
6. Checkpoint periodically and on pause, seek, song/queue change; take final
   state before engine shutdown and flush writes. A crash may lose the most
   recent checkpoint interval; clean exit must save the latest playhead.
7. Retain paused metadata for missing files/unavailable sources; fail through
   normal playback handling only after Play, not during startup.
8. Add isolated tests with fake decoder/resolver/sink and isolated preferences:
   no startup resolve/output, explicit Play at position, seek before Play,
   old queue compatibility, corrupt/empty data, bounded queue/index, identity
   mismatch, write ordering, party isolation, and shutdown behavior.

### C. Settings audit and implementation

Approved controls:

- Downloads opening tab: `downloads_opening_tab`, default Playlists; choices
  Playlists, Songs, Artists, Albums, Last used. `downloads_last_tab` records tab
  clicks even when another default is selected. Applies on reopening Downloads;
  Local Music retains its own default.
- `downloads_auto_update_on_open` defaults off. After a successful live collection
  open, follow all continuation pages before comparing the merged snapshot; only
  enqueue new tracks when the merged order changes. Existing downloaded files and
  removed tracks remain, failure leaves the record intact, and no background
  checks occur. Manual Update remains available. Spotify is checked after its
  page's matching jobs finish; unresolved tracks are excluded as before.
- Open download folder opens the existing `~/Music/BitChord` location with the
  platform desktop API and reports failure; it does not relocate files.
- Restore last session defaults on and applies on the next launch; restored
  playback is paused. Disabling does not imply deleting the saved snapshot.
- Existing pitch-black and Replay genres controls are not duplicated. Window
  memory and PiP shortcuts were not selected and are deliberately excluded.

#### Artwork privacy

- The existing persisted master remains unchanged. Strength has three bounded,
  persisted choices, defaulting to the previous medium strength. Browsing,
  expanded player, and PiP/external are independent persisted scopes; all default
  on for existing installations. Compact and top-bar player artwork use the player scope.
  Browsing and queue covers use the browsing scope; the shared expanded player provides a
  player-scoped composition for its covers and separately guards video and motion.
- The explicit reveal button reveals selected main-window areas for ten seconds;
  Hide now cancels it. Reveal is memory-only, never automatic, and never affects
  PiP or external media. Disabling the master cancels any active reveal.
- The auxiliary scope omits artwork from PiP, MPRIS, Windows SMTC and Discord
  rather than relying on external blur. Text metadata remains visible;
  third-party clients may retain previously cached artwork. The setting wording
  explicitly avoids a blanket privacy guarantee.
- Privacy changes republish media-control metadata without waiting for a track
  change. Desktop artwork clears its previously decoded image immediately when
  switching into privacy mode. A focused external-artwork policy test was added.
- Artist hero and motion paths are suppressed under browsing privacy. Additional
  desktop image/video paths still require an exhaustive audit; do not treat the
  master as a guarantee that all identifiable imagery is hidden.

### D. Verification and handoff

- Baseline and final JVM suites:
  `JAVA_HOME=~/jdk21 PATH=~/jdk21/bin:$PATH ./gradlew :shared:jvmTest :sharedUi:jvmTest :desktopApp:test --console=plain`.
- Use Xvfb for relevant Compose/native visual tests; report environmental
  dependency/network failures separately from test failures.
- Review the combined diff for shared-Android regressions and overlapping
  preference/session writes. Verify no credentials or real user state in tests.
- Record exact test counts/results and remaining GNOME Ubuntu/Fedora manual
  checks. This headless host cannot certify physical trackpad behavior.
- Append detailed files, decisions, verification, and limitations to memory.

### E. Settings back-route audit

- Settings' page host now supplies a visible back control for every non-main
  `DesktopSettingsPage`: equalizer, audio output, Listen Together, lyrics sources,
  translation language, Spotify setup, integrations, and licenses. Dialogs opened
  on top remain dialogs and retain their own close/cancel controls. The graphics
  and blur control currently lives inline in Appearance, not on a separate page.
- Settings-origin children return to the preserved main Settings list and search;
  Spotify setup opened from Integrations returns there first. Direct Spotify
  sidebar links use ordinary window history. Back does not leave a party: that
  remains a separate explicit action in the room.
- Nested ancestry is tracked synchronously and excluded from ordinary window
  history, preventing return loops; the visible control and Alt+Left use the same
  parent-first route. `DesktopSettingsNavigationTest` covers all enum children,
  nested integration ancestry, direct entries, labels and history policy, plus
  the full Home → Settings → Integrations → Spotify → Back → Back → Back
  sequence and a direct Spotify sidebar round trip. Page and ancestry changes
  are applied together in Compose snapshots so the collector sees coherent state.
- Verification pending the coordinated final Gradle suite. Manually check each
  child, in-party navigation, direct Spotify links, popup dismissal, keyboard
   focus/Alt+Left, and narrow/scrolling layouts under a graphical session.

### F. Authorized delivery workflow

1. Work on `feat/desktop-black-resume-settings`; do not commit directly to master.
2. Perform local verification and code review; document failures and fixes. On
   this memory-constrained host, complete platform verification in GitHub CI
   rather than misrepresenting the aborted local desktop build as passing.
3. Commit and push the feature branch to `kartik2005221/BitChord`, then open a PR
   against `master` with the implementation and verification summary.
4. Inspect repository CI (including Windows and Android coverage unavailable on
   this Linux desktop-only host), fix actionable failures on the same branch,
   and merge only after required checks pass. Distinguish any pre-existing
   external integration failures with evidence, not assumptions.
5. Verify the merged commit and clean local master, append outcomes to memory.
   Historical release notes/images remain unchanged.
6. The user subsequently authorized **v1.12.0 draft preparation**: after green
   checks and merge, tag the merged release commit, run the release pipeline,
   attach the new notes and all six platform binaries plus checksums, verify
   `draft=true`, and stop without publishing. Use a fresh actual-app screenshot.
