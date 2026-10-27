import { SITE_ORIGINS } from '../site.config';

/**
 * Sites this extension serves beyond the build's own list (site.config.ts), added at
 * runtime from the fork's popup. Trust has three legs, and they follow the same set:
 *
 * 1. the background's sender allowlist (entrypoints/background.ts) — reads this live;
 * 2. the request-rewriting rules (lib/rules.ts) — their initiatorDomains are
 *    reinstalled whenever the list changes;
 * 3. Firefox's relay content script (entrypoints/relay.content.ts), which carries the
 *    page's window messages into the extension — registered onto a brand-new host with
 *    `browser.contentScripts.register`, because content-script matches can't be revised
 *    in an installed manifest.
 *
 * Chromium has no dynamic messaging for web pages: `externally_connectable` is only
 * read at install time. So there, a site the build doesn't know can still get its
 * requests rewritten (leg 2) but can't talk to the extension until a build includes
 * it (BITCHORD_SITE_ORIGINS) — which is what the served zips do. Store builds keep
 * this list read-only: their manifest is the store's, and the list is what a review
 * approved.
 */

/** The key saved extras live under. */
export const SAVED_ORIGINS_KEY = 'siteOrigins';

/** Hostnames the build already serves; dynamic relay registration skips them. */
const BUILD_HOSTS = new Set(SITE_ORIGINS.map((origin) => new URL(origin).hostname));

/** The relay content script's path in the Firefox build (WXT drops the `.content` suffix), for dynamic registration. */
const RELAY_FILE = 'content-scripts/relay.js';

/** The origin of a plain http(s) site URL, port included, or null when input isn't one. */
export function normalizeOrigin(input: string): string | null {
  try {
    const url = new URL(input.trim());
    return url.protocol === 'https:' || url.protocol === 'http:' ? url.origin : null;
  } catch {
    return null;
  }
}

export async function savedOrigins(): Promise<string[]> {
  const stored = await browser.storage.local.get(SAVED_ORIGINS_KEY);
  const values = stored[SAVED_ORIGINS_KEY];
  return Array.isArray(values) ? values.filter((value): value is string => typeof value === 'string') : [];
}

/** Every trusted origin: the build's, then this profile's extras. */
export async function trustedOrigins(): Promise<string[]> {
  const extras = await savedOrigins();
  return [...SITE_ORIGINS, ...extras.filter((origin) => !SITE_ORIGINS.includes(origin))];
}

export type AddOutcome = { list: string[]; error: 'invalid' | 'duplicate' | null };

/** Normalise, keep and remember an origin; duplicates (including built ones) are refused. */
export async function addOrigin(input: string): Promise<AddOutcome> {
  const current = await savedOrigins();
  const origin = typeof input === 'string' ? normalizeOrigin(input) : null;
  if (origin === null) return { list: current, error: 'invalid' };
  if (SITE_ORIGINS.includes(origin) || current.includes(origin)) {
    return { list: current, error: 'duplicate' };
  }
  const list = [...current, origin];
  await browser.storage.local.set({ [SAVED_ORIGINS_KEY]: list });
  return { list, error: null };
}

/** Take an origin out of the saved extras. Build defaults belong to the manifest. */
export async function removeOrigin(origin: string): Promise<string[]> {
  const list = (await savedOrigins()).filter((saved) => saved !== origin);
  await browser.storage.local.set({ [SAVED_ORIGINS_KEY]: list });
  return list;
}

/** A handle per dynamically served relay, so a removed site's registration is let go. */
type RegisteredRelay = { unregister: () => Promise<void> };

/** Firefox's `browser.contentScripts` — the WXT browser types don't carry it, and only Firefox has it. */
type FirefoxContentScripts = {
  register: (options: { matches: string[]; js: { file: string }[]; runAt: 'document_start' }) => Promise<RegisteredRelay>;
};

function contentScripts(): FirefoxContentScripts | null {
  return (browser as unknown as { contentScripts?: FirefoxContentScripts }).contentScripts ?? null;
}

const dynamic = new Map<string, RegisteredRelay>();

/** Firefox: put the relay onto hosts the manifest didn't match — and only there. */
export async function registerExtraRelays(origins: string[]): Promise<void> {
  if (!import.meta.env.FIREFOX) return;
  const scripts = contentScripts();
  if (!scripts) {
    console.warn("BitChord: this build's types lack browser.contentScripts, so added sites can't be relayed");
    return;
  }
  const wanted = new Map<string, string>();
  for (const origin of origins) {
    let host: string | null = null;
    try {
      host = new URL(origin).hostname;
    } catch {
      continue;
    }
    if (host && !BUILD_HOSTS.has(host)) wanted.set(host, origin);
  }
  if (!dynamic.size && !wanted.size) return;

  for (const host of [...dynamic.keys()]) {
    if (wanted.has(host)) continue;
    const script = dynamic.get(host);
    dynamic.delete(host);
    try {
      await script?.unregister();
    } catch {
      // Already gone (browser restarted): nothing to do.
    }
  }
  for (const [host, origin] of wanted) {
    if (dynamic.has(host)) continue;
    try {
      const pattern = `${new URL(origin).protocol}//${host}/*`;
      dynamic.set(
        host,
        await scripts.register({ matches: [pattern], js: [{ file: RELAY_FILE }], runAt: 'document_start' }),
      );
    } catch (error) {
      console.warn(`BitChord: registering the relay on ${host} failed`, error);
    }
  }
}
