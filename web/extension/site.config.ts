/** The web player's production origin. */
export const PRODUCTION_ORIGIN = 'https://bitchord.in';

/** This fork's web player (the fork's own Cloudflare Pages deploy). */
export const FORK_PAGES_ORIGIN = 'https://bitchord-web.pages.dev';

/** The same site at its Cloudflare Pages address (the production branch's deploy). */
const PAGES_ORIGIN = 'https://bitchord-8bw.pages.dev';

/** The Vite dev server (web/app/vite.config.ts pins the port). */
const DEV_ORIGIN = 'http://localhost:5173';

/**
 * Node's env holds the build flags, but a bare `process.env` reference throws
 * in a browser bundle (no `process` global) — and a ReferenceError at module
 * load took the extension's whole background with it once. Both reads are
 * guarded, so this evaluates wherever the build pulls it: Node, or a bundle.
 */
const env: Record<string, string | undefined> | undefined = typeof process === 'undefined' ? undefined : process.env;

/**
 * The web app origins this extension serves. Every message is checked against
 * this list, and it becomes externally_connectable (Chromium), the relay's
 * content-script matches (Firefox) and the DNR rules' initiatorDomains.
 *
 * The fork's own player first, then bitchord.in and its pages.dev address, and the
 * dev server too except in a store build (BITCHORD_STORE_BUILD=1), so a
 * published extension never answers a page on localhost.
 * BITCHORD_SITE_ORIGINS (comma-separated) replaces the list. The first origin
 * is the one the popup and homepage_url link to.
 */
export const SITE_ORIGINS: string[] = (
  env?.BITCHORD_SITE_ORIGINS ??
  [FORK_PAGES_ORIGIN, PRODUCTION_ORIGIN, PAGES_ORIGIN, ...(env?.BITCHORD_STORE_BUILD === '1' ? [] : [DEV_ORIGIN])].join(',')
)
  .split(',')
  .map((o) => o.trim().replace(/\/+$/, ''))
  .filter(Boolean);

/**
 * Match patterns can't carry a port, so `http://localhost:5173` becomes
 * `http://localhost/*`. The exact origin, port included, is still enforced at
 * runtime against SITE_ORIGINS.
 */
export function matchPatternsFor(origins: string[]): string[] {
  return [...new Set(origins.map((o) => {
    const url = new URL(o);
    return `${url.protocol}//${url.hostname}/*`;
  }))];
}
