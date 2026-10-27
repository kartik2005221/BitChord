# Fork web line — TODO

The fork's web line lives on this branch (`web`), deployed to Cloudflare Pages
(`bitchord-web.pages.dev`) with the party server self-hosted on Render
(`bitchord-ktk.onrender.com`). Upstream `main` stays mirrored by the fork's
`main`; everything fork-specific happens here.

## Done

- [x] Branch `web` off upstream main (web player + extension + backend)
- [x] Deployment live: Cloudflare Pages ← branch `web`, pnpm build:site, app/dist
- [x] Party server on Render with `JAM_ALLOWED_ORIGINS` for the site (+ localhost dev)
- [x] Extension working on the fork's site and upstream's (`BITCHORD_SITE_ORIGINS` build env)

## In progress — extension mini settings

- [x] Trusted-origins plumbing (`lib/trustedOrigins.ts`) — effective origins = build defaults ∪ saved
- [x] Background: sender allowlist + DNR rules + Firefox relay follow the saved set, live
- [x] Popup: Settings group listing origins, add/remove (fork builds; store builds read-only)

## Next

- [ ] JioSaavn source: TS port of Android's `JioSaavnService` (search, album/playlist, DES stream decrypt, 320/96 pick) into `web/app/src/data/saavn/`
- [ ] JioSaavn in search/results UI + playback glue
- [ ] Extension DNR rule + `host_permissions` for `jiosaavn.com` / `*.saavncdn.com` (same shape as the YouTube rules)
- [ ] Popup: JioSaavn toggle once the source lands
- [ ] Web app settings page: party-server override display
- [ ] When a custom domain is bound: add it here and in each deploy env
