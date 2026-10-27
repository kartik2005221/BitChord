import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  durationText,
  fetchSaavnSong,
  fetchSaavnStream,
  prioritizeExplicit,
  searchSaavnSongs,
  selectBestSaavnStream,
  songArtist,
  songImage,
} from './api';
import { decryptUrl } from './decrypt';
import type { RawSongItem } from './types';

/**
 * The real thing, captured from the API itself: "Tum Hi Ho" (Aashiqui 2)'s
 * search row and its DES body,Chrome's UA, X-Forwarded-For, Cookie: explicit_content=1
 * — so a key or scheme change upstream fails here first, with a URL
 * every machine can decode identically.
 */
const CAPTURED = 'ID2ieOjCrwfgWvL5sXl4B1ImC5QfbsDyRofh8YQPTqjR2tIxTYXqGEQHgqnNfsetouo4rsD0s4RZKObWAyHrEhw7tS9a8Gtq';
const CAPTURED_URL = 'https://aac.saavncdn.com/430/5c5ea5cc00e3bff45616013226f376fe_96.mp4';

type RawRow = { more_info: Record<string, unknown> } & Record<string, unknown>;

/** One search row, exactly as the server words it (the parse path takes it apart field by field). */
function rawSong(overrides: Record<string, unknown> = {}): RawRow {
  const more_info = {
    album_id: '1139549',
    album: 'Aashiqui 2',
    encrypted_media_url: CAPTURED,
    duration: '262',
    '320kbps': 'true',
    artistMap: {
      primary_artists: [
        { id: '702592', name: 'Mithoon' },
        { id: '459320', name: 'Arijit Singh' },
        { name: '' },
      ],
    },
  } as Record<string, unknown>;
  return {
    id: 'aRZbUYD7',
    title: 'Tum Hi Ho',
    image: 'https://c.saavncdn.com/430/Aashiqui-2-Hindi-2013-150x150.jpg',
    explicit_content: '0',
    more_info,
    ...overrides,
  };
}

function reply(value: unknown) {
  return vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(value))));
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('decryptUrl', () => {
  it('decodes a real encrypted_media_url under the fixed key', () => {
    expect(decryptUrl(CAPTURED)).toEqual(CAPTURED_URL);
  });

  it('answers nothing for nothing', () => {
    expect(decryptUrl('')).toEqual('');
    expect(decryptUrl('   ')).toEqual('');
  });

  it('answers nothing when the base64 is not a URL', () => {
    expect(decryptUrl('////')).toEqual('');
  });
});

describe('selectBestSaavnStream', () => {
  it('rewrites the rendition to 320 when the catalogue says it exists', () => {
    expect(selectBestSaavnStream(CAPTURED_URL, true)).toEqual({
      url: 'https://aac.saavncdn.com/430/5c5ea5cc00e3bff45616013226f376fe_320.mp4',
      kbps: 320,
    });
  });

  it('keeps the URL it decoded and labels only what it names', () => {
    expect(selectBestSaavnStream(CAPTURED_URL, false)).toEqual({ url: CAPTURED_URL, kbps: 96 });
    expect(selectBestSaavnStream('https://aac.saavncdn.com/x.mp4', true)).toEqual({ url: 'https://aac.saavncdn.com/x.mp4', kbps: null });
    expect(selectBestSaavnStream('', true)).toEqual(null);
  });

  it('reads a rendition past a cache-buster', () => {
    const withBuster = `${CAPTURED_URL}?cdn=m&expires=1`;
    const stream = selectBestSaavnStream(withBuster, true);
    expect(stream?.kbps).toEqual(320);
    expect(stream?.url.startsWith('https://aac.saavncdn.com/430/5c5ea5cc00e3bff45616013226f376fe_320.mp4')).toEqual(true);
  });
});

describe('prioritizeExplicit', () => {
  it("puts explicit rows first, preserving each group's order", () => {
    const rows = [rawSong(), rawSong({ id: 'b', explicit_content: '1', title: 'A (Explicit)' }), rawSong({ id: 'c' }), rawSong({ id: 'd', explicit_content: '1', title: 'B (Explicit)' })];
    const order = prioritizeExplicit(rows as unknown as RawSongItem[]).map((song) => song.id);
    expect(order).toEqual(['b', 'd', 'aRZbUYD7', 'c']);
  });

  it("reads either wording of the explicit flag", () => {
    const rows = prioritizeExplicit([rawSong({ id: 'e', explicit_content: 'true' }), rawSong() ] as unknown as RawSongItem[]);
    expect(rows[0]?.id).toEqual('e');
  });
});

describe('searchSaavnSongs', () => {
  it('normalises the server’s wording and drops rows without an identity', async () => {
    reply({
      results: [
        rawSong(),
        rawSong({ title: '' }),
        rawSong({ id: 'x', explicit_content: '1', image: 'http://c.saavncdn.com/x_50x50.jpg' }),
      ],
    });
    const rows = await searchSaavnSongs('tum hi ho');
    expect(rows.map((song) => song.id)).toEqual(['x', 'aRZbUYD7']); // the explicit one ahead, the anonymous one gone
    expect(songImage(rows[0]!)).toEqual('https://c.saavncdn.com/x_500x500.jpg');
    expect(rows[1]?.more_info.artistMap.primary_artists.map((artist) => artist.name)).toEqual(['Mithoon', 'Arijit Singh']);
  });

  it('answers with no rows on a failure', async () => {
    reply({ status: 'failure', results: undefined });
    expect(await searchSaavnSongs('noise')).toEqual([]);
  });
});

describe('fetchSaavnSong / fetchSaavnStream', () => {
  it('reads the map-keyed envelope song.getDetails really answers with', async () => {
    const song = rawSong();
    reply({ aRZbUYD7: song });
    const parsed = await fetchSaavnSong('aRZbUYD7');
    expect(parsed?.title).toEqual('Tum Hi Ho');
    expect(songArtist(parsed!)).toEqual('Mithoon, Arijit Singh');
    expect(durationText(262)).toEqual('4:22');
  });

  it('reads the songs-array envelope too', async () => {
    reply({ songs: [rawSong()] });
    expect((await fetchSaavnSong('aRZbUYD7'))?.title).toEqual('Tum Hi Ho');
  });

  it('decrypts the stream and reports the rendition it rewrote to', async () => {
    reply({ aRZbUYD7: rawSong() });
    expect(await fetchSaavnStream('aRZbUYD7')).toEqual({
      url: 'https://aac.saavncdn.com/430/5c5ea5cc00e3bff45616013226f376fe_320.mp4',
      kbps: 320,
    });
  });

  it("a song is never labelled 320 its catalogue row doesn't offer", async () => {
    reply({
      aRZbUYD7: rawSong({ more_info: { ...rawSong().more_info, '320kbps': 'false', encrypted_media_url: '' } }),
    });
    expect(await fetchSaavnStream('aRZbUYD7')).toEqual(null);
    reply({ aRZbUYD7: rawSong({ more_info: { ...rawSong().more_info, '320kbps': 'false' } }) });
    expect((await fetchSaavnStream('aRZbUYD7'))?.kbps).toEqual(96);
  });
});
