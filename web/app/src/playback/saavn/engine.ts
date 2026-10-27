import { fetchSaavnStream, MIN_USABLE_KBPS } from '@/data/saavn/api';
import type { AudioEngine } from '@/playback/engine';
import type { LoadedTrack } from '@/playback/youtube/engine';
import { timed, trackLog } from '../trackLog';

/** How long to wait for the stream before calling it dead (YouTube's engine waits the same). */
const FIRST_AUDIO_TIMEOUT_MS = 15_000;

const superseded = (): Error => {
  const error = new Error('Replaced by a newer load');
  error.name = 'SupersededError';
  return error;
};

/** The codec under the container's usual name (`aac`/`mp4` are AAC). */
function codecOf(url: string): string {
  return /\.mp3(?=[?#]|$)/i.test(url) ? 'MP3' : 'AAC';
}

/**
 * JioSaavn's own playback in the browser (the fork): the decoded CDN URL as
 * the deck `<audio>`'s source, nothing between it and the bytes. There is no
 * picture (videoStream is always null) and no loudness metadata, so AutoMix
 * levels by its own analysis of the stream itself.
 */
export class SaavnEngine implements AudioEngine {
  readonly audio: HTMLAudioElement;
  onFatal: (videoId: string, message: string, permanent: boolean) => void = () => {};
  onRecovered: (track: LoadedTrack) => void = () => {};

  private generation = 0;

  constructor(audio: HTMLAudioElement) {
    this.audio = audio;
  }

  /**
   * Loads `saavn:<songId>` — the search row's namespaced id — and starts it
   * at [startSec]. Refusals follow the Android source: a song without a
   * stream, or below the usable bitrate, is the caller's error to fall back
   * from, never a stream at half a label.
   */
  async load(videoId: string, { startSec = 0, autoplay = true } = {}): Promise<LoadedTrack> {
    const generation = ++this.generation;
    const songId = videoId.slice('saavn:'.length);
    const audio = this.audio;
    const stream = await timed('saavn stream', songId, () => fetchSaavnStream(songId));
    const reason = !stream
      ? 'JioSaavn answered no stream for this song'
      : stream.kbps !== null && stream.kbps <= MIN_USABLE_KBPS
        ? `JioSaavn serves this song at ${stream.kbps}kbps, below the usable floor`
        : null;
    if (!stream || reason) {
      trackLog.w(reason ?? 'no stream', songId);
      throw new Error(reason ?? 'JioSaavn answered no stream for this song');
    }

    try {
      audio.src = stream.url;
      audio.load();
      if (startSec > 0) {
        await this.metadata(generation);
        if (generation === this.generation) audio.currentTime = startSec;
      }
      if (autoplay) {
        await audio.play().catch((error: unknown) => {
          // Autoplay without a gesture is refused; the track is loaded and the play button will start it.
          if (error instanceof DOMException && error.name === 'NotAllowedError') {
            trackLog.w('autoplay was blocked by the browser; waiting for a tap', songId);
          } else throw error;
        });
      }
      await this.firstAudio(generation);

      const container = codecOf(stream.url);
      return {
        videoId,
        title: '',
        author: '',
        durationSec: Number.isFinite(audio.duration) ? Math.round(audio.duration) : 0,
        thumbnails: [],
        client: 'saavn',
        format: `${stream.kbps ?? 'unknown'}kbps ${container}`,
        stream: { codec: container, itag: null, bitrateKbps: stream.kbps, sampleRateHz: 44100, channels: 2, mode: 'direct' },
        loudnessDb: null,
      };
    } catch (error) {
      if (generation === this.generation) trackLog.e(`saavn load failed: ${String(error)}`, songId);
      throw error;
    }
  }

  async stop(): Promise<void> {
    this.generation++;
    const audio = this.audio;
    audio.removeAttribute('src');
    audio.load();
  }

  videoStream(_height?: number | null): Promise<null> {
    return Promise.resolve(null);
  }

  /** Resolves when the element has data to play; rejects on a timeout, a media error, or a newer load. */
  private firstAudio(generation: number): Promise<void> {
    const audio = this.audio;
    if (audio.readyState >= HTMLMediaElement.HAVE_FUTURE_DATA) return Promise.resolve();
    return new Promise((resolve, reject) => {
      let settled = false;
      const finish = (error?: Error) => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        audio.removeEventListener('canplay', onCanPlay);
        audio.removeEventListener('error', onError);
        if (generation !== this.generation) reject(superseded());
        else reject(error);
      };
      const onCanPlay = () => finish();
      const onError = () => finish(new Error("The browser couldn't read JioSaavn's stream for this song"));
      const timer = setTimeout(
        () => finish(new Error(`no audio within ${FIRST_AUDIO_TIMEOUT_MS / 1000}s`)),
        FIRST_AUDIO_TIMEOUT_MS,
      );
      audio.addEventListener('canplay', onCanPlay);
      audio.addEventListener('error', onError);
    });
  }

  /** Resolves once the length is known, for a resume past it. */
  private metadata(generation: number): Promise<void> {
    const audio = this.audio;
    if (audio.readyState >= HTMLMediaElement.HAVE_METADATA) return Promise.resolve();
    return new Promise((resolve, reject) => {
      let settled = false;
      const finish = (error?: Error) => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        audio.removeEventListener('loadedmetadata', onLoaded);
        audio.removeEventListener('error', onError);
        if (generation !== this.generation) reject(superseded());
        else reject(error);
      };
      const onLoaded = () => finish();
      const onError = () => finish(new Error("The browser couldn't read JioSaavn's stream for this song"));
      const timer = setTimeout(
        () => finish(new Error(`no metadata within ${FIRST_AUDIO_TIMEOUT_MS / 1000}s`)),
        FIRST_AUDIO_TIMEOUT_MS,
      );
      audio.addEventListener('loadedmetadata', onLoaded);
      audio.addEventListener('error', onError);
    });
  }
}
