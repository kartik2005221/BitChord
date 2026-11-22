# Known issues and verification gaps

## Replay album key collision · reproduced

Two different releases named **Shared Album**, with different artists and distinct album IDs, crash the desktop Replay list on the AWT event thread when Top albums is composed. Reproduced independently with **All time** and **This year** in the packaged Linux app on an X11 dummy display using an isolated Java preferences profile. Both trials were repeated with the distributable built directly from desktop master `926489c1`, using an isolated 1280 × 800 X11 display. Each showed a blocking error dialog. The JVM was still running when the screenshot was taken. [All time screenshot](evidence/replay-all-time.png) · [This year screenshot](evidence/replay-this-year.png)

**Fixture and reproduction.** In a disposable test profile seed the listening-stats buckets with track A, artist One, album ID id-a, album “Shared Album”, 180,000 ms and two plays in November 2026. Add track B, artist Two, album ID id-b, same album title, 160,000 ms and two plays in October 2026. Open Library → Replay → All time and scroll until both album rows enter composition. On a fresh launch repeat with This year in 2026. For a later test run set both fixture dates in the chosen test year. Do not seed a real listening history.

The fresh-build logs for both periods contain the same event-thread exception.

```text
Exception in thread "AWT-EventQueue-0" java.lang.IllegalArgumentException: Key "albums-Shared Album" was already used. If you are using LazyColumn/Row please make sure you provide a unique key for each item.
    at androidx.compose.ui.internal.InlineClassHelperKt.throwIllegalArgumentException(InlineClassHelper.kt:36)
    at androidx.compose.ui.layout.LayoutNodeSubcompositionsState.subcompose(SubcomposeLayout.kt:1592)
```

DesktopListeningStats groups by album identity while DesktopReplayUi builds lazy-list keys from the display title. Preserve two genuinely different releases. Test a single logical album split across ID/name-only history separately, including legacy rows. Reproducing this with dummy data does not imply the supplied screenshots contain a failing dataset. [Desktop ranking](../../desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopListeningStats.kt) · [Desktop rows](../../desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopReplayUi.kt) · [Original correction](https://github.com/kushagrasinghx/BitChord/commit/60587a74)

## Spotify pins after reconnect · established by source

A same-account reconnection with a new sp_dc credential can hide previously pinned playlists. Pin ownership uses a hash of that credential. Account-relative Liked Songs makes automatic reassignment based on a matching playlist ID unsafe. In item 12, migrate while a verified binding from the old account is available. Keep unresolved pins and fail closed across accounts. Test same-account token rotation, different-account reconnect, offline snapshots, restart and removal. A live two-login migration has not been performed at A0. [Pin scopes](../../desktopApp/src/main/kotlin/com/music/bitchord/desktop/DesktopPlaylistPins.kt)

## Port-specific risks

| Area | Current evidence | Verification needed |
| --- | --- | --- |
| Liked downloads | VLLM is account-relative. Desktop collection mutations can be keyed by browse ID alone. A collection-only ownership check cannot prove a file has no standalone manual claim. | Multiple accounts, overlapping playlist/manual claims, incompletely paged likes, quota, retry and disable during transfer. |
| Editing long playlists | The later-page response can arrive as an append action. An early parse failure can resemble end-of-list. Deduplication by song ID can erase a repeated entry. | A 500+ fixture with repeated songs and different setVideoIds, failed/repeated continuation tokens, owner change and partial reorder. |
| Artwork | The two user-provided expanded-player frames clip lettering. LandscapeArtwork uses Crop. The scan already prefers per-file embedded art. | Compare original wide still and square cover, cache miss and failed maximum rendition, rapid skips, blur/privacy and marquee over time. |
| Model delivery | Two real model files verified. Existing CI tests require bundled weights. Upstream follows a latest-release pointer and checks cached length. | Pinned asset and notice lifetime, SHA-verified cache, missing second model, low disk, inference from installed RPM and Windows packages after first enable. |
| Native audio and Replay | Linux decoder registry lacks AC-4. Upstream receiver accounting uses platform-specific state. | Real device playback/PCM levels, Windows codec matrix, real system-routed speakers and physical Fedora audio outputs. |
| Environment | Provider source and tests can establish request handling. Signed-in YouTube/Spotify, third-party integrations, GNOME Wayland scaling, tray, audio hardware and 144 Hz need their own observations. | Record each as executed, failed, blocked or inapplicable in the smoke checklist. Capture service and platform gaps in each feature PR and the Part A report. |

See the [manual smoke plan](SMOKE_CHECKLIST.md) and [screenshot intake](SCREENSHOT_INTAKE.md) for reproducible scenarios and visual evidence limits.
