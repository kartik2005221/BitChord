# Fork web line — TODO

The fork's web line lives on **`master`** (trunk for web, and since today for
desktop too), deployed to Cloudflare Pages (`bitchord-web.pages.dev`) with the
party server self-hosted on Render (`bitchord-ktk.onrender.com`). Two ways to
watch it: a PR merged to `master` deploys if Pages is pointed at `master`
(done from Cloudflare Pages → Settings → Production branch), or keep building
the `web` snapshot branch — `master` is pushed onto `web` each milestone until
the cutover. Upstream `main` stays mirrored by the fork's `main`; everything
fork-specific happens here. JioSaavn is the fork's own source line; its
remaining work is the "Next" section below.

## Done

- [x] Branch `master` off the web line (extension 0.5.0 included); `main` mirrors upstream
- [x] Deployment live: Cloudflare Pages, party server on Render with `JAM_ALLOWED_ORIGINS`
- [x] Extension working on the fork's site and upstream's (`BITCHORD_SITE_ORIGINS` build env)
- [x] Extension popup: Settings section listing trusted sites, add/remove at
      runtime (fork builds; store builds read-only), DNR + sender allowlist +
      the Firefox relay all follow the saved list live; 0.5.0
- [x] **JioSaavn service** (`data/saavn/`): the Android contract ported —
      search + song details on the catalogue's own endpoints, DES/ECB/PKCS5
      stream-URL decryption (crypto-js), rendition spliced to `_320` only when
      the row offers one, explicit rows first, ≤96 kbps refused (the
      YouTube-Music floor). Verified against the live API: the captured row's
      real encrypted URL decrypts to the real CDN URL; 14 tests, typecheck green.
- [x] **JioSaavn search shelf** (`SongResults.saavn`): doesn't disturb any
      YouTube bucket — rows play straight from `saavncdn` (`saavn:<songId>` id
      namespace, the player routes it like the Android resolver does), and a
      dead JioSaavn is an empty shelf, never a broken search. Player deck
      routing (RoutingEngine) has YouTube first-use lazy and keeps crossfade,
      AutoMix and video-follow rows safe across both source families.
- [x] **The background boots again (0.7.0)**: `site.config.ts` read
      `process.env` at module scope — a browser has no `process`, so every
      extension since 0.5.0 died on load (`ReferenceError: process is not
      defined`, captured on real Firefox): no listener, popup rows hanging on
      "Checking…", detection timing out → "NeedsExtension". Runtime origins
      now come from the build's `__SITE_ORIGINS__` define (the manifest's
      single source of truth), the env reads are guarded, and both built
      bundles are booted in a process-less VM that answers a real bridge ping
      (8 tests). Real-Firefox harness: the crash before, clean load after.
- [x] **The "High quality" mark, drawn as the phone and desktop draw it**:
      between the seek bar's timestamps in the full-screen player when the
      stream playing is at 256 kbps or more (the shared NerdStats rule —
      YouTube web streams don't reach it), and on JioSaavn search rows before
      the credits wherever the row declared its 320kbps rendition, with the
      separator `SourceQualityBadge` and the rows use: badge, " · ", then the
      names.
- [x] **The desktop line unfreezes onto master**: upstream deleted the desktop
      app after v1.9.0 (`desktopApp`/`desktopUi`, their three-platform check
      jobs, the tag-driven release packaging and the desktop docs), so the
      merge restores the lot — the modified Spotify files win their
      modify/delete conflicts, the gradle includes come back, and the check
      jobs + `release.yml` (deb/rpm/AppImage and friends on a tag) ride on
      trunk again. Desktop fixes and releases happen on master from here; the
      `desktop` branch stays as a frozen reference. Also made the fork's CI
      actually run on master — `check.yml`/`web.yml` triggered on upstream's
      `main` only, so every master push before this ran on local suites alone.

## Next

- [ ] **Desktop fixes on master** (patch releases via tags → `release.yml`,
      e.g. a v1.9.1): the GNOME dock icon / `WM_CLASS` mismatch in the deb's
      launcher — issue #1 — and more as they're reported; reports live in
      issues, fixes land on trunk.

- [ ] **JioSaavn parity with Android + desktop, logically**:
      Android keeps the source opt-in (off) with a source settings card, and
      treats its rows as another voice inside the unified results — the web
      side so far is a separate "JioSaavn" shelf plus a popup-only toggle
      (the quality mark itself has landed). Re-shape: in-app source settings
      (bridge write method), then decide the default (match
      Android's off, or keep the fork's on).
- [x] Extension DNR rule + `host_permissions` for the api.php and
      `*.saavncdn.com` (same shape as the YouTube rules; the CORS answer and
      the impersonation headers both arrive there). Until that lands, JioSaavn
      needs the extension installed: the page alone can't set
      `X-Forwarded-For`/`Cookie`, so its fetches are refused.
- [x] Popup: JioSaavn toggle (a Sources row) + the page's ask-before-mix via
      the bridge (`saavn.enabled`), on by default.
- [x] **Playback follow-ups**: a JioSaavn row that refuses (no stream, under
      the 96 kbps floor) falls back instead of erroring — matched to its
      YouTube Music version with the Spotify importer's machinery and swapped
      into the queue in place. AutoPlay's radio seeds ask with the row's
      YouTube cut the same way, and with no match the station stays quiet
      rather than warning on every track.
- [x] Web app settings page: the Listen Together section shows the party
      server — this deployment's (`bitchord-ktk.onrender.com`), or a custom
      one a joined room rode in on.
- [ ] When a custom domain is bound: add it here and in each deploy env
- [ ] Upstream PRs, when the work is ready to present: cut `pr/<fix>` topic
      branches off `main` carrying only the upstream-worthy subsets (the
      JioSaavn source leans on the fork's extension rules; a standalone
      shape needs checking first).
- [ ] Larger JioSaavn surface beyond search rows — album/playlist pages on
      the catalogue (Android's service itself only does search and song
      details, so those pages need new endpoint contracts first).
