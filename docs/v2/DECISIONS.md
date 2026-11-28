# Desktop v2 decision log

## Gate A0

| Decision or proposed default | Reason and consequence |
| --- | --- |
| Use original v1.10 and desktop PR #13 as the comparison points. | The release is mostly Android. Pinning both source trees gives reviewers a reproducible comparison. Keep the desktop batch as the starting point. |
| Use desktop-native entry points. | Radio belongs in the existing sidebar navigation. Live Search song actions belong in existing pointer/context and keyboard controls. Spotify Canvas, lyrics preview and time display belong in existing grouped Settings. |
| Prioritize data integrity and the reproducible Replay failure. | Distinct releases can share a title. Playlist entries can repeat a song ID. Account-relative collections can reuse an ID across accounts. Preserve the correct kind of identity for each operation. |
| Propose opt-in, additive liked downloads. | Use the signed-in YouTube Music list as authority. Preview a large first fill, expose a storage budget, pause on low disk, and keep completed files on unlike or switch-off. Manual downloads and other collections can claim the same file. |
| Keep a model-only AutoMix port. | The weights account for about 21.7 MiB of compressed desktop payload. A platform runtime rebuild has a different risk and payoff. Preserve offline playback, validate hashes and retain model attribution. |
| Test supported local audio artwork now and defer a new OS file-opening integration unless requested at A0. | Scanned local audio already takes an embedded cover first. File-manager associations, existing-instance handoff and foreign paths need independent design and packaging tests on both OSes. |
| Keep native AC-4 enablement and a new remote-speaker transport out of the proposed beta port. | Linux's shipped desktop decoder has no AC-4 entry. Desktop has no equivalent to the Android Cast/DLNA/watch receiver clock. Existing E-AC-3 and system audio routing remain within the smoke matrix. |
| Limit shared changes to demonstrably common behavior. | Continuation parsing, signed-in request parameters, and lyric selection/translation are suitable shared candidates. The desktop recording matcher is separate, so adapt its own call path. Keep desktop queue presentation, controls, preferences and motion behind desktop entry points. |
| Address Spotify reconnect at the account/playlist boundary in item 12. | A cookie-derived fingerprint changes with new credentials. Attach new pins to a stable verified account ID when available. Migrate a legacy scope only with evidence binding both identities. Preserve unresolved records for safe recovery. |
| Keep the original exclusion list and the approval gates. | No Drive, Premium Opus, new queue gestures, animated lyrics sharing, AutoMix artwork transition system or other A2 items are dependencies of the proposed port. The redesign, performance baseline milestones and prerelease require the later Part B approval. |

## Gate A1

| Decision | Reason and consequence |
| --- | --- |
| Use desktop merge criteria. | Both desktop jobs must pass, including shared and shared UI JVM suites. The Android PR job is advisory. No TV, Wear or web PR jobs are configured. On 2026-11-23, the fork's default branch `master` exposed no required-check contexts and the rulesets list was empty. The connector could read branch metadata but returned 403 for the detailed protection endpoint. |
| Scope Gate A1 to desktop. | Keep shared edits small for desktop maintenance and future upstream pulls. This supersedes the mobile-impact practice proposed in A0. |
| Keep automatic liked downloads off by default. | Before opt-in, show the first-fill size estimate and free space. Enforce the configured storage cap, preserve completed downloads on disable or unlike, and pause when space runs low. |
| Land AutoMix model delivery last. | Compare paired before/after Windows and Linux installers and extracted sizes built under identical packaging conditions. Record the size deltas first and recommend skipping implementation if savings are small. |
| Stop at Gate A1 for a test build. | Replay lands as a standalone PR first. Finish only the approved correctness group before the maintainer tries the combined build. Larger features wait for Gate A1 approval. |
| Stop at the Part A report. | Hold releases, tags, prereleases, overhaul implementation and other Part B work for separate approval. |

**Part B proposal for M5, pending approval.** Remove CI jobs for unshipped platforms from the desktop fork. This focuses CI execution on shipped desktop targets while leaving platform sources available for future upstream pulls.

Proposal, source links, sequencing and validation details are in the [port matrix](UPSTREAM_PORT.md). Record later approvals or deviations here with the relevant commit or PR.

## Gate A1 retest

The maintainer approved the menu, Replay, reorder, Spotify pins and download-count corrections. The retest also includes Thumbs down, the inline mini-player quality indicator and Part A item 15 for player quick-add.

**Gate 1 docked player prototypes.** Show one dock aligned to the sidebar's right edge and a second spanning beneath the sidebar. Reserve the measured dock height in either layout, including with the queue open. Compare track, seek, transport, Like, quick-add, queue and volume in the bar. Compare shuffle, repeat, output, PiP and additional track actions in the overflow at narrow widths.
