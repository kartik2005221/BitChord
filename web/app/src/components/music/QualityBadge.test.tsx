import { describe, expect, it } from 'vitest';
import type { MusicItem } from '@/data/music/types';
import { HIGH_QUALITY_TEXT, isHighQualityStream, itemQualityStated } from './QualityBadge';

const base: MusicItem = {
  kind: 'song',
  id: 'v1',
  title: 'Track',
  subtitle: 'Album • Artist',
  artists: [],
  album: null,
  thumbnail: null,
  wide: false,
  durationSec: 200,
  explicit: false,
};

describe('isHighQualityStream', () => {
  it('refuses a stream the deck has not resolved yet', () => {
    expect(isHighQualityStream(null)).toBe(false);
    expect(isHighQualityStream({ bitrateKbps: null })).toBe(false);
  });

  it('refuses YouTube web streams under the hi-quality floor', () => {
    expect(isHighQualityStream({ bitrateKbps: 141 })).toBe(false);
    expect(isHighQualityStream({ bitrateKbps: 255 })).toBe(false);
  });

  it('accepts a stream at or above the floor (NerdStats HI_QUALITY_KBPS)', () => {
    expect(isHighQualityStream({ bitrateKbps: 256 })).toBe(true);
    expect(isHighQualityStream({ bitrateKbps: 320 })).toBe(true);
  });
});

describe('itemQualityStated', () => {
  it('holds for a saavn row that declared its 320kbps rendition', () => {
    expect(itemQualityStated({ ...base, kind: 'saavn-song', id: 'saavn:1', quality: '320' })).toBe(true);
  });

  it('fails when the rendition was not stated, or the flag said no', () => {
    expect(itemQualityStated({ ...base, kind: 'saavn-song', id: 'saavn:2' })).toBe(false);
    expect(itemQualityStated({ ...base, kind: 'saavn-song', id: 'saavn:3', quality: null })).toBe(false);
  });

  it('is the fork rows\' mark alone — a YouTube row never states it', () => {
    expect(itemQualityStated({ ...base, quality: '320' })).toBe(false);
  });
});

describe('the mark', () => {
  it('reads "High quality", the phone\'s string', () => {
    expect(HIGH_QUALITY_TEXT).toBe('High quality');
  });
});
