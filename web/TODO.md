# Fork web line — TODO

The fork's web line lives on **`master`** (trunk for web + the coming desktop
resurrection), deployed to Cloudflare Pages (`bitchord-web.pages.dev`) with the
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

## Next

- [ ] **Playback follow-ups**: YouTube fallback when a Saavn row refuses
      (quality gate or API miss — match to YouTube with the Spotify folder's
      machinery); AutoPlay radio after a Saavn track (radio is YouTube's, so
      its seed needs a YT match; today it quietly does nothing instead of
      erroring).
- [ ] Extension DNR rule + `host_permissions` for the api.php and
      `*.saavncdn.com` (same shape as the YouTube rules; the CORS answer and
      the impersonation headers both arrive there). Until that lands, JioSaavn
      needs the extension installed: the page alone can't set
      `X-Forwarded-For`/`Cookie`, so its fetches are refused.
- [ ] Popup: JioSaavn toggle once the source lands (today it's on wherever the
      extension serves).
- [ ] Web app settings page: party-server override display
- [ ] When a custom domain is bound: add it here and in each deploy env
