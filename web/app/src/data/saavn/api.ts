import { decryptUrl } from './decrypt';
import type { RawArtistMapItem, RawSongItem, SaavnStream } from './types';

/**
 * The JioSaavn catalogue (the Android app's JioSaavnService.kt, ported):
 * search and per-song details, and each song's decoded CDN stream.
 *
 * Two endpoints, both GETs on the same base with fixed parameters. The page
 * may only set the simple headers (Accept, Accept-Language); the ones a page
 * isn't allowed to set — `X-Forwarded-For`, `X-Real-IP`, `Cookie`, and the
 * browser `User-Agent` — arrive through the extension's declarativeNetRequest
 * rules (web/extension/lib/rules.ts), which are also what makes the answer
 * CORS-readable. Without the extension these calls just miss.
 *
 * Like the Kotlin original, every export is total: search returns [], the
 * others return null on any error, so a dead JioSaavn never breaks a search
 * or a queue.
 */

/** The catalogue API's base URL, kept encoded in the source as the Android app's is. */
const BASE_URL = atob('aHR0cHM6Ly93d3cuamlvc2Fhdm4uY29tL2FwaS5waHA=');

/** The fixed parameters every call adds after the endpoint's own (JioSaavnService's defaults). */
const COMMON: Record<string, string> = {
  _format: 'json',
  _marker: '0',
  api_version: '4',
  ctx: 'android',
};

/** Renditions under this aren't worth having (kotlin's MIN_USABLE_KBPS): YouTube Music's 160kbps Opus wins. */
export const MIN_USABLE_KBPS = 96;

/** The rendition pattern inside a decoded URL, e.g. `…/…_96.mp4` (Kotlin's rendition regex verbatim). */
const RENDITION = /_(48|96|160|320)\.(mp4|aac|mp3)(?=[?#]|$)/i;

// ---- Raw JSON normalisation ----------------------------------------------------------

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

/** A field however the server words it (string or number), else ''. */
function field(record: Record<string, unknown> | null, key: string): string {
  const value = record?.[key];
  return typeof value === 'string' ? value : typeof value === 'number' && Number.isFinite(value) ? String(value) : '';
}

function parseArtist(value: unknown): RawArtistMapItem | null {
  if (!isRecord(value)) return null;
  return { id: field(value, 'id'), name: field(value, 'name') };
}

/**
 * One catalogue row with every Kotlin default applied. A row without an id
 * and a title has no identity on the web (the queue keys on a namespaced id),
 * so it's dropped here rather than handed up unplayable.
 */
function parseSong(value: unknown): RawSongItem | null {
  if (!isRecord(value)) return null;
  const id = field(value, 'id');
  const title = field(value, 'title');
  if (!id || !title) return null;
  const more_info = isRecord(value.more_info) ? value.more_info : {};
  const artistMap = isRecord(more_info.artistMap) ? more_info.artistMap : {};
  const primary = Array.isArray(artistMap.primary_artists) ? artistMap.primary_artists : [];
  return {
    id,
    title,
    image: field(value, 'image'),
    explicit_content: field(value, 'explicit_content'),
    more_info: {
      album_id: field(more_info, 'album_id'),
      album: field(more_info, 'album'),
      encrypted_media_url: field(more_info, 'encrypted_media_url'),
      duration: field(more_info, 'duration'),
      '320kbps': field(more_info, '320kbps'),
      artistMap: {
        primary_artists: primary.map(parseArtist).filter((artist): artist is RawArtistMapItem => !!artist?.name),
      },
    },
  };
}

// ---- Calls ---------------------------------------------------------------------------

async function api(params: Record<string, string>): Promise<unknown> {
  const url = new URL(BASE_URL);
  for (const [key, value] of Object.entries({ ...COMMON, ...params })) url.searchParams.set(key, value);
  try {
    const response = await fetch(url, {
      headers: { Accept: 'application/json', 'Accept-Language': 'en-IN,en;q=0.9' },
      credentials: 'omit',
      referrerPolicy: 'no-referrer',
      signal: AbortSignal.timeout(8000),
    });
    if (!response.ok) return null;
    return JSON.parse(await response.text()) as unknown;
  } catch {
    return null;
  }
}

/**
 * Ten catalogue rows for [query], explicit cuts ahead of their clean
 * duplicates (Kotlin's prioritizeExplicit). The server answers at least ten
 * rows however small `n` is asked, so this asks for ten, as the Android app
 * does.
 */
export async function searchSaavnSongs(query: string): Promise<RawSongItem[]> {
  const json = (await api({ __call: 'search.getResults', q: query, p: '1', n: '10' })) as
    | { results?: unknown[] }
    | null;
  return prioritizeExplicit(((Array.isArray(json?.results) ? json.results : []) as unknown[]).map(parseSong).filter((song) => !!song));
}

/** The one catalogue row for a song id. */
export async function fetchSaavnSong(saavnSongId: string): Promise<RawSongItem | null> {
  const json = await api({ __call: 'song.getDetails', pids: saavnSongId });
  if (!isRecord(json)) return null;
  // song.getDetails answers an envelope keyed by the id asked for —
  // `{"<pid>": {…}}`; the `{"songs": […]}` shape is the search's. Both are read, as the Android side does.
  const songs = Array.isArray(json.songs) ? json.songs : [];
  return parseSong(songs[0]) ?? Object.values(json).map(parseSong).find((song) => !!song) ?? null;
}

/** The song's decoded CDN URL, or null: never throws, never serves a guessed label. */
export async function fetchSaavnStream(saavnSongId: string): Promise<SaavnStream | null> {
  const song = await fetchSaavnSong(saavnSongId);
  if (!song) return null;
  return selectBestSaavnStream(decryptUrl(song.more_info.encrypted_media_url), supports320(song));
}

// ---- Decoded streams -----------------------------------------------------------------

/**
 * The stream [decryptedUrl] will really serve: the URL as-is with the kbps
 * its rendition names, or, when the catalogue says a 320kbps rendition
 * exists, the same URL rewritten to `…_320.<ext>` and labelled 320 (Kotlin's
 * selectBestSaavnStream). An unrecognised URL is never labelled anything, and
 * a blank decryption is the caller's null.
 */
export function selectBestSaavnStream(decryptedUrl: string, has320: boolean): SaavnStream | null {
  if (!decryptedUrl) return null;
  const match = RENDITION.exec(decryptedUrl);
  if (!match) return { url: decryptedUrl, kbps: null };
  const offered = Number(match[1]);
  if (!has320) return { url: decryptedUrl, kbps: offered };
  // Splice the rendition in place: a cache-buster after `.mp4` counts on it.
  const url = decryptedUrl.slice(0, match.index) + `_320.${match[2]}` + decryptedUrl.slice(match.index + match[0].length);
  return { url, kbps: 320 };
}

/** Whether the upload's own rendition of [song] is 320kbps (`"320kbps": "true"`; asking anyway doesn't make one). */
export function supports320(song: RawSongItem): boolean {
  return song.more_info['320kbps'].toLowerCase() === 'true';
}

/** Uncensored rows ahead of their clean duplicates; Array sort is stable, like Kotlin's sortedByDescending. */
export function prioritizeExplicit(songs: RawSongItem[]): RawSongItem[] {
  return [...songs].sort((a, b) => Number(isExplicit(b)) - Number(isExplicit(a)));
}

/** `"1"`, `"0"` of the explicit flag. */
export function isExplicit(song: RawSongItem): boolean {
  return song.explicit_content === '1' || song.explicit_content.toLowerCase() === 'true';
}

/** The artwork at its largest size, https (the server's 150x150 / 50x50 crops). */
export function songImage(song: RawSongItem): string {
  return song.image.replace(/150x150|50x50/g, '500x500').replace(/^http:\/\//, 'https://');
}

/** The primary credits joined as YouTube words them, or the shape an unnamed row needs. */
export function songArtist(song: RawSongItem): string {
  return song.more_info.artistMap.primary_artists.map((artist) => artist.name).join(', ') || 'Unknown Artist';
}

/** The length in seconds, or null. */
export function songDurationSec(song: RawSongItem): number | null {
  const seconds = Number.parseInt(song.more_info.duration, 10);
  return Number.isFinite(seconds) ? seconds : null;
}

/** "M:SS", how the Android words a track length. */
export function durationText(seconds: number | null): string {
  if (seconds === null || seconds <= 0) return '';
  const min = Math.floor(seconds / 60);
  const sec = String(seconds % 60).padStart(2, '0');
  return `${min}:${sec}`;
}
