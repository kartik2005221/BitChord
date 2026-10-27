import { best, type MatchCandidate } from '@/data/match/trackMatcher';
import { searchSongs } from '@/data/music/api';
import type { Track } from '@/data/music/types';

/** One row's answer, remembered per recording (the Spotify folder's shape). */
const cache = new Map<string, Track | null>();
const CACHE_MAX = 400;

/** A row as a matching target, as the Spotify importer words one. */
function candidateOf(track: Track): MatchCandidate {
  return {
    title: track.title,
    artist: track.artists.map((artist) => artist.name).join(', '),
    durationSec: track.durationSec,
    album: track.album?.name ?? null,
    explicit: track.explicit,
  };
}

/**
 * A JioSaavn row's YouTube Music version, or null: the search's best-scoring
 * song by title, artist, length and album — failing that its first hit (the
 * Spotify importer's shape, SpotifyImporter.matchTrack).
 *
 * Fallback only: the row is asked for this after JioSaavn itself wouldn't
 * serve it (playback/saavn's refusal gate), and the answer replaces the row
 * in place, so the queue keeps one identity per recording. Android's
 * resolver does the same when its own source refuses (SourceResolver walks
 * to the next source).
 */
export async function matchSaavnToYouTube(row: Track): Promise<Track | null> {
  const cached = cache.get(row.videoId);
  if (cache.has(row.videoId)) return cached ?? null;
  const target = candidateOf(row);
  const query = [target.title, target.artist].filter((part) => part.trim()).join(' ');
  const candidates = await searchSongs(query);
  const found =
    best(
      candidates.map((candidate) => ({ ...candidateOf(candidate), candidate })),
      target,
    )?.candidate ?? candidates[0] ?? null;
  cache.set(row.videoId, found);
  if (cache.size > CACHE_MAX) cache.delete(cache.keys().next().value!);
  return found;
}
