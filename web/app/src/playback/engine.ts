import type { SaavnEngine } from './saavn/engine';
import type { LoadedTrack, VideoStream, YouTubeEngine } from './youtube/engine';

/**
 * What a deck plays through, whatever the source: the YouTube player (Shaka
 * over googlevideo) so far, and the fork's own sources beside it. The two
 * sets share the deck's `<audio>` element, which is why an engine switch
 * mustn't overlap: the one handing over stops before the other loads
 * (RoutingEngine). Deck bodies only ever see this interface.
 */
export interface AudioEngine {
  readonly audio: HTMLAudioElement;
  load(videoId: string, { startSec, autoplay }?: { startSec?: number; autoplay?: boolean }): Promise<LoadedTrack>;
  stop(): Promise<void>;
  /** Raised when a track can't be played at all, after every fallback. */
  onFatal: (videoId: string, message: string, permanent: boolean) => void;
  /** Raised when the engine moved a track elsewhere mid-playback. */
  onRecovered: (track: LoadedTrack) => void;
  /** A music video's picture, or null: JioSaavn's rows have none. */
  videoStream(height?: number | null): Promise<VideoStream | null>;
}

/** A queue row's ids own their source besides YouTube's, by prefix. */
const SAAVN_PREFIX = 'saavn:';

/**
 * The engine a deck actually holds: it keeps one player per source set,
 * creates each on first need, hands the deck's element from one to the other,
 * and answers everything else from the one that loaded last.
 */
export class RoutingEngine implements AudioEngine {
  readonly audio: HTMLAudioElement;
  onFatal: (videoId: string, message: string, permanent: boolean) => void = () => {};
  onRecovered: (track: LoadedTrack) => void = () => {};

  private yt: YouTubeEngine | null = null;
  private saavn: SaavnEngine | null = null;
  /** The engine the last load went to; the element belongs to it until another takes over. */
  private current: AudioEngine | null = null;

  constructor(audio: HTMLAudioElement) {
    this.audio = audio;
  }

  private async forVideo(videoId: string): Promise<AudioEngine | null> {
    if (videoId.startsWith(SAAVN_PREFIX)) {
      this.saavn ??= this.created(new (await import('./saavn/engine')).SaavnEngine(this.audio));
      return this.saavn;
    }
    if (!this.yt) {
      const { YouTubeEngine } = await import('./youtube/engine');
      this.yt = this.created(new YouTubeEngine(this.audio));
    }
    return this.yt;
  }

  /** Engine callbacks pass straight through to the deck's own handlers. */
  private created<T extends AudioEngine>(engine: T): T {
    engine.onFatal = (videoId, message, permanent) => this.onFatal(videoId, message, permanent);
    engine.onRecovered = (track) => this.onRecovered(track);
    return engine;
  }

  async load(videoId: string, { startSec = 0, autoplay = true } = {}): Promise<LoadedTrack> {
    const engine = await this.forVideo(videoId);
    if (!engine) throw new Error(`Nothing knows how to play ${videoId}`);
    if (engine !== this.current) {
      // The element belongs to one engine at a time: the old one stops before the new one loads.
      for (const other of [this.yt, this.saavn]) {
        if (other && other !== engine) await other.stop().catch(() => {});
      }
      this.current = engine;
    }
    return engine.load(videoId, { startSec, autoplay });
  }

  async stop(): Promise<void> {
    this.current = null;
    for (const engine of [this.yt, this.saavn]) {
      if (engine) await engine.stop().catch(() => {});
    }
  }

  async videoStream(height: number | null = null): Promise<VideoStream | null> {
    // The picture is YouTube's own: without a loaded YouTube response there is none.
    return this.yt ? this.yt.videoStream(height) : null;
  }
}
