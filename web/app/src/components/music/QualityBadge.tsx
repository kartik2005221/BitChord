import type { MusicItem } from '@/data/music/types';
import type { StreamStats } from '@/playback/youtube/engine';

/** Re-exported here, where rows read it from. */
export type { SourceQuality } from '@/data/music/types';

/**
 * The "High quality" mark (sharedUi's SourceQualityBadge on the phone and the
 * desktop): a small headphones glyph beside the label. Between the seek bar's
 * timestamps it names the stream actually playing, and on JioSaavn rows it
 * sits before the credits — the Android search rows draw it the same way
 * (SearchScreen shows the mark where other-source rows join the results).
 *
 * The threshold is NerdStats' rule: at or above 256 kbps, without lossless, a
 * stream reads as high quality. YouTube's web streams don't reach it, so they
 * draw the mark nowhere, like the phone under its hi-quality floor; a
 * JioSaavn row's 320kbps rendition does.
 */
const HIGH_QUALITY_KBPS = 256;

export function isHighQualityStream(stream: Pick<StreamStats, 'bitrateKbps'> | null | undefined): boolean {
  return (stream?.bitrateKbps ?? 0) >= HIGH_QUALITY_KBPS;
}

/** Whether this row draws the mark: the catalogue's rows that stated a hi-quality rendition. */
export function itemQualityStated(item: MusicItem): boolean {
  return item.kind === 'saavn-song' && item.quality === '320';
}

/** The headphones glyph, as the player's badge draws it: a band over two cups. */
export function HeadphonesIcon({ size = 10, className }: { size?: number; className?: string }) {
  return (
    <svg className={className} width={size} height={size} viewBox="0 0 16 16" aria-hidden="true">
      <path d="M2.75 13.2V8.6a5.25 5.25 0 0 1 10.5 0v4.6" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" />
      <path
        d="M2.1 9.5h.5c.88 0 1.6.72 1.6 1.6v1.9c0 .88-.72 1.6-1.6 1.6h-.5A1.6 1.6 0 0 1 .5 13v-1.9c0-.88.72-1.6 1.6-1.6Zm11.3 0h.5c.88 0 1.6.72 1.6 1.6V13c0 .88-.72 1.6-1.6 1.6h-.5a1.6 1.6 0 0 1-1.6-1.6v-1.9c0-.88.72-1.6 1.6-1.6Z"
        fill="currentColor"
      />
    </svg>
  );
}

/** "High quality", the mark's label (sharedUi's high_quality string). */
export const HIGH_QUALITY_TEXT = 'High quality';

/**
 * The row's mark: the badge leads, and the credits follow after a separator —
 * Common.kt's `SourceQualityBadge` + `" · $subtitle"` shape.
 */
export function RowQualityMark({ item }: { item: MusicItem }) {
  if (!itemQualityStated(item)) return null;
  return (
    <>
      <HeadphonesIcon size={10} className="me-1 inline-block shrink-0 align-[-1px]" />
      <span className="shrink-0">{HIGH_QUALITY_TEXT}</span>
      <span className="shrink-0">&nbsp;·&nbsp;</span>
    </>
  );
}
