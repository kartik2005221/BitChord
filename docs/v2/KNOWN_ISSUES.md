# Known issues and verification gaps

## Replay album key collision

On the PR #13 baseline, two different releases named **Shared Album**, with different artists and distinct album IDs, crashed the desktop Replay list on the AWT event thread when Top albums is composed. Reproduced independently with **All time** and **This year** in the packaged Linux app on an X11 dummy display using an isolated Java preferences profile. Both trials were repeated with the distributable built directly from desktop master `926489c1`, using an isolated 1280 × 800 X11 display. Each showed a blocking error dialog. The JVM was still running when the screenshot was taken. [All time screenshot](evidence/replay-all-time.png) · [This year screenshot](evidence/replay-this-year.png)

**Fixture and reproduction.** In a disposable test profile seed the listening-stats buckets with track A, artist One, album ID id-a, album “Shared Album”, 180,000 ms and two plays in November 2026. Add track B, artist Two, album ID id-b, same album title, 160,000 ms and two plays in October 2026. Open Library → Replay → All time and scroll until both album rows enter composition. On a fresh launch repeat with This year in 2026. For a later test run set both fixture dates in the chosen test year. Do not seed a real listening history.

The fresh-build logs for both periods contain the same event-thread exception.

```text
Exception in thread "AWT-EventQueue-0" java.lang.IllegalArgumentException: Key "albums-Shared Album" was already used. If you are using LazyColumn/Row please make sure you provide a unique key for each item.
    at androidx.compose.ui.internal.InlineClassHelperKt.throwIllegalArgumentException(InlineClassHelper.kt:36)
    at androidx.compose.ui.layout.LayoutNodeSubcompositionsState.subcompose(SubcomposeLayout.kt:1592)
```

The ranking already distinguished the releases. The list used the displayed title as a row key. Desktop commit `72a49ee8` carries the ranking identity through to the row. With the same history, the packaged Linux build displayed both artists and their separate listening totals in both periods. The regression test failed against the previous key and passed with the fix. [All time after the fix](evidence/replay-fixed-all-time.png) · [This year after the fix](evidence/replay-fixed-this-year.png) · [Original correction](https://github.com/kushagrasinghx/BitChord/commit/60587a74)

## Spotify pins after reconnect

PR #13 scoped pins to a session cookie fingerprint, so rotating credentials could hide a playlist. The reconnect change verifies the Spotify account ID before associating pins with that account. A known cookie may be migrated when the same account is verified. If the old login was never associated with an account, its records are retained and can remain hidden. Test same-account rotation, restart while offline, and switching to a different account with separate Liked Songs. The fixture tests passed on Linux. Live verification with two Spotify accounts is outstanding. [Reconnect PR](https://github.com/kartik2005221/BitChord/pull/19)

## Playlist menu on a virtual display

The playlist overflow menu did not stay visibly open on the isolated Linux display with either the baseline or PR #18 package. Row removal and confirmation worked in the package. Compose tests exercised the editing controls. Check the menu on Fedora GNOME/Wayland with a disposable playlist before relying on a real account edit.

## Port-specific risks

| Area | Current evidence | Verification needed |
| --- | --- | --- |
| Liked downloads | VLLM is account-relative. Desktop collection mutations can be keyed by browse ID alone. A collection-only ownership check cannot prove a file has no standalone manual claim. | Multiple accounts, overlapping playlist/manual claims, incompletely paged likes, off-by-default state, first-fill estimate with unknown sizes, enforced cap, retry and disable during transfer. |
| Editing long playlists | The later-page response can arrive as an append action. An early parse failure can resemble end-of-list. Deduplication by song ID can erase a repeated entry. | The 501-entry packaged harness passed with a repeated song across two pages and an external reorder before save. A live owner change and interrupted remote write still need a disposable account playlist. |
| Artwork | The two user-provided expanded-player frames clip lettering. LandscapeArtwork uses Crop. The scan already prefers per-file embedded art. | Compare original wide still and square cover, cache miss and failed maximum rendition, rapid skips, blur/privacy and marquee over time. |
| Model delivery | Two real model files verified. Existing CI tests require bundled weights. Upstream follows a latest-release pointer and checks cached length. | Pinned asset and notice lifetime, SHA-verified cache, missing second model, low disk, inference from installed RPM and Windows packages after first enable, plus paired installer and extracted-size measurements on both platforms. |
| Native audio and Replay | Linux decoder registry lacks AC-4. Upstream receiver accounting uses platform-specific state. | Real device playback/PCM levels, Windows codec matrix, real system-routed speakers and physical Fedora audio outputs. |
| Environment | Provider source and tests can establish request handling. Signed-in YouTube/Spotify, third-party integrations, GNOME Wayland scaling, tray, audio hardware and 144 Hz need their own observations. | Record each as executed, failed, blocked or inapplicable in the smoke checklist. Capture service and platform gaps in each feature PR and the Part A report. |

The fork’s default branch `master` showed zero required status checks in branch metadata and the ruleset list was empty. The detailed protection endpoint returned 403. Gate A1 requires verification of both desktop jobs before merge. The Android PR job is advisory. No TV, Wear or web PR jobs are configured.

See the [manual smoke plan](SMOKE_CHECKLIST.md) and [screenshot intake](SCREENSHOT_INTAKE.md) for reproducible scenarios and visual evidence limits.
