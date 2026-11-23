# Desktop smoke checklist · Gates A0 and A1

Use master `926489c1` for the initial baseline and repeat the affected cases on each merge commit. Record the exact revision, package, date, tester, display scale, audio device and test profile. The target workstation is Fedora 44, GNOME/Wayland, 1920 × 1080 at 144 Hz. Keep results from a Linux X11 fixture distinct from native Wayland and real-device observations.

## Preparation

- Use the same tracks and configuration on both runs. Use disposable playlists, a clean profile, and a second existing test profile to exercise persistence. Keep screenshots and logs free of tokens, cookies and personal listening history.
- For a source build, use JDK 21 and the [desktop build instructions](../../DESKTOP.md). For an installed build, record its format and version. Prepare working audio, a tagged folder, an inaccessible folder, a writable Downloads location and a deliberately unwritable test destination.
- For integration coverage, use test YouTube Music and Spotify playlists, a Last.fm account, a running signed-in Discord desktop client and two Listen Together clients. Record whether the configured addon, custom module, seeded module, GNOME tray host and physical media keys are available.
- Mark each observation PASS, FAIL, BLOCKED or N/A. For failures, record the expected and observed behavior and reproduction steps. For blocked or inapplicable cases, record the missing service, hardware or platform behavior.

## Sources and quality

Desktop catalogue search uses YouTube Music. Stream resolution can race providers and upgrade during playback. For each trial, record the requested quality, actual source, codec/bitrate and whether the source changed after playback started.

| Selected quality | Eligible providers for catalogue fallback |
| --- | --- |
| Low / Medium | YouTube Music |
| High | JioSaavn and YouTube Music |
| Lossless | Enabled user addons, custom/seeded modules, JioSaavn and YouTube Music |

- [ ] For each configured source, search a known recording, select a permitted quality, play, download and verify the resulting file offline. Record fallback separately if the intended source could not serve it.
- [ ] Enable multiple sources and remove a match or make one unavailable. Verify that the fallback is the same recording. With a playback-only addon, verify the download uses an eligible source or reports failure.
- [ ] Test each quality and restart to verify persistence. Check the actual codec before recording a lossless result. Verify the HQ lookup indicator and settled quality indicator against the expanded player.

## Playback, queue, downloads and files

- [ ] Play, pause, resume, seek, skip, change volume and let a song finish. Observe audible output, track identity, artwork and playhead. Exercise repeat and shuffle.
- [ ] Add distinct tracks and a duplicate to the queue, reorder, remove, advance and clear. Check visible and audible order and saved queue behavior.
- [ ] Quit mid-track with paused restoration enabled. Relaunch and check the personal track, queue and position before pressing Play. Disable restore, enable it again, and verify retained session data. A temporary party session should not become the restored personal queue.
- [ ] Use Like and More in the mini player. Check the full player and the corresponding song menu for the same track and rating.
- [ ] Download a track and a playlist. Check progress, completion, playable offline files and retry after network or write failure. Cancel one queued and one active transfer and inspect the completion state.
- [ ] In a fresh profile, confirm automatic liked downloads are off. Before enabling, check the first-fill count, size estimate, treatment of missing size data, and free space. Exercise a low cap, low disk, retry, overlapping manual/playlist claims, and a switched account. Disable or unlike during an active run and check that completed downloads are retained.
- [ ] Scan and play tagged local audio. Verify per-file embedded covers, folder fallback, restart persistence and recovery after folder access is restored. Cancel folder selection and check the previous library.

## Accounts and desktop integration

- [ ] Connect Spotify, open Liked Songs and a playlist, and play a matched item. Verify sign-out, canceled login and session recovery. Use two disposable accounts for owner-bound state. Record the audio source independently of Spotify catalogue identity.
- [ ] Play long enough to scrobble once to Last.fm. Verify it appears on the service. Disable the integration and exercise denied/expired authorization.
- [ ] With Discord running, inspect activity through track change, pause and resume. Disable presence and check that activity clears. Quit and restart the client to exercise IPC recovery.
- [ ] Host and join Listen Together from separate clients. Check track, play/pause, seek, skip, leave and rejoin. Exercise an invalid invite and loss of the host/service.
- [ ] Use GNOME MPRIS controls (or `playerctl`) and physical media keys for transport. Check audio and metadata. With a supported tray host, hide, restore and quit the app. Record which GNOME tray extension or host was used.
- [ ] Check for an update, inspect its notes, cancel and resume using the app. Exercise RPM installer handoff/cancel on disposable Fedora. On a disposable AppImage test an actual controlled upgrade, relaunch and retention of data. Check recovery from an unavailable network or unwritable staging path.

## Sidebar, settings and sorting

- [ ] Test Back and Refresh in both sidebar sizes with Queue or Now Playing open. Verify Home retains cards while refreshing, Library reloads and unsupported actions show their expected state.
- [ ] Open nested Settings, change a reversible setting, go back and relaunch. Check persistence, focus and access to the visible navigation controls as the window is resized.
- [ ] Pin, rename, open and unpin local and account playlists. Verify titles and identities after restart. Switch accounts and check visibility. Record same-account Spotify credential rotation against the [known limitation](KNOWN_ISSUES.md).
- [ ] In Downloads → Playlists use distinct names and dates. Exercise A–Z, Z–A and Added in both grid and list. Open an item and go back. Repeat in any collections that share the modified sort path.

## Automated checks and run record

The Linux CI job enables native PiP checks. With JDK 21, X11 dependencies and the project wrapper, run the desktop and shared suites and packaged regression.

```sh
BITCHORD_PIP_NATIVE_TEST=1 xvfb-run -a ./gradlew :shared:jvmTest :sharedUi:jvmTest :desktopApp:test "-Pbitchord.version=test-visua"
./gradlew :desktopApp:createDistributable "-Pbitchord.version=test-visua"
bash desktopApp/packaging/tests/linux-automix-check.sh
```

Gate A1 requires successful Linux and Windows desktop checks, including `:shared:jvmTest` and `:sharedUi:jvmTest` in both jobs. Android, TV, Wear and web CI are outside the A1 merge criteria. Record the exact command, exit code, test report and any platform gaps. [CI definition](../../.github/workflows/check.yml)

For the last A1 change, compare paired before/after AutoMix installers on Windows and Linux. Match format, toolchain, packaging and signing conditions. Record revisions, each installer and extracted size, and absolute and percentage deltas. Verify first-enable download, installed inference, restart and offline fallback. Use test builds for the comparison. Publication waits for separate approval.

| Record per baseline or rerun | Details |
| --- | --- |
| Revision and environment | Commit, PR, package, OS/session, resolution/scale, output device, date and tester |
| Fixtures and scope | Test track IDs or sanitized descriptions, sources, profile, affected cases |
| Results | Status per case, observation and evidence link, difference from baseline, and reason for each gap |
| Automation | Exact commands and results, JUnit output and package checks |

## A0 execution record

| Run on the PR #13 baseline | Result | Evidence and limit |
| --- | --- | --- |
| Fresh Linux JVM build with JDK 21, Xvfb and native PiP checks enabled | PASS | Gradle ran `:desktopApp:test` (775 tests), `:shared:jvmTest` (78) and `:sharedUi:jvmTest` (42). JUnit XML reported zero skipped, errors or failures. `:desktopApp:createDistributable` completed in the same invocation. |
| Packaged Linux AutoMix regression | PASS | The shipped analyzer loaded through JNI. The existing 120 BPM fixture, downbeat ordering and PCM resampling checks passed (reported 120.003 BPM). This exercises the existing bundled-weights implementation. |
| Replay on Linux X11 using a synthetic history | FAIL | The fresh distributable showed a blocking duplicate album-key error dialog for All time and This year. See [fixture and exception](KNOWN_ISSUES.md). This case is missing from the green baseline suite. |
| Signed-in integrations, two-client party, Fedora/GNOME/Wayland, hardware outputs and other manual scenarios | UNTESTED at A0 | Run the relevant subset against comparable profiles before and after the first affected feature. |
| Windows and Android | No new A0 execution | This branch contains documentation. The earlier [PR #13 checks](https://github.com/kartik2005221/BitChord/pull/13/checks) describe that merge. The A1 desktop checks need a fresh run for each feature PR. |
