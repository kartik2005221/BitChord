/**
 * The JioSaavn catalogue's raw JSON, exactly the fields the port consumes.
 * Mirrors the Android app's Raw* data classes (JioSaavnService.kt); the
 * server words most flags as `"1"`/`"0"` and sizes as strings.
 */

export interface RawArtistMapItem {
  id: string;
  name: string;
}

/** The primary credits of a song, as `more_info.artistMap.primary_artists`. */
export interface RawArtistMap {
  primary_artists: RawArtistMapItem[];
}

export interface RawMoreInfo {
  album_id: string;
  album: string;
  /** DES-encrypted CDN URL; decryptUrl() decodes it. */
  encrypted_media_url: string;
  /** The length in seconds, as a string. */
  duration: string;
  /** Whether a 320kbps rendition exists, as `"true"`/`"false"`. */
  '320kbps': string;
  artistMap: RawArtistMap;
}

export interface RawSongItem {
  id: string;
  title: string;
  image: string;
  /** `"1"` or `"0"`. */
  explicit_content: string;
  more_info: RawMoreInfo;
}

/** A decoded CDN URL and the bitrate it will really deliver. */
export interface SaavnStream {
  url: string;
  /** The rendition the URL names, or null when it doesn't name one. */
  kbps: number | null;
}
