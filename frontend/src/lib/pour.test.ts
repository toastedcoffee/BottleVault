import { describe, it, expect } from 'vitest';
import { parseSizeToMl, percentAfterPour, defaultPourOz } from './pour';

describe('parseSizeToMl', () => {
  it('parses common size labels', () => {
    expect(parseSizeToMl('750ml')).toBe(750);
    expect(parseSizeToMl('750 ml')).toBe(750);
    expect(parseSizeToMl(' 750ML ')).toBe(750);
    expect(parseSizeToMl('1L')).toBe(1000);
    expect(parseSizeToMl('1.75L')).toBe(1750);
    expect(parseSizeToMl('70cl')).toBe(700);
    expect(parseSizeToMl('500ml')).toBe(500);
  });

  it('rejects unparseable or degenerate sizes', () => {
    expect(parseSizeToMl(null)).toBeNull();
    expect(parseSizeToMl(undefined)).toBeNull();
    expect(parseSizeToMl('')).toBeNull();
    expect(parseSizeToMl('a fifth')).toBeNull();
    expect(parseSizeToMl('750')).toBeNull(); // no unit - ambiguous
    expect(parseSizeToMl('0ml')).toBeNull();
    expect(parseSizeToMl('12 oz')).toBeNull(); // oz sizes not supported yet
  });
});

describe('percentAfterPour', () => {
  it('computes a standard 1.5 oz pour from a 750ml bottle', () => {
    // 1.5 oz = 44.36ml = 5.91% of 750ml -> 62 - 5.91 = 56.09 -> 56
    expect(percentAfterPour(62, 750, 1.5)).toBe(56);
    expect(percentAfterPour(100, 750, 1.5)).toBe(94);
  });

  it('computes a 5 oz wine pour from a 750ml bottle', () => {
    // 5 oz = 147.87ml = 19.72% of 750ml -> 60 - 19.72 = 40.28 -> 40
    expect(percentAfterPour(60, 750, 5)).toBe(40);
  });

  it('clamps at empty when the pour exceeds what is left', () => {
    expect(percentAfterPour(3, 750, 1.5)).toBe(0);
    expect(percentAfterPour(1, 750, 5)).toBe(0);
  });

  it('always decrements by at least one point', () => {
    // 0.5 oz of a 5L bottle is 0.3% - rounding alone would be a no-op
    expect(percentAfterPour(60, 5000, 0.5)).toBe(59);
  });

  it('never goes below zero', () => {
    expect(percentAfterPour(0, 750, 1.5)).toBe(0);
  });
});

describe('defaultPourOz', () => {
  it('defaults wine to a 5 oz glass', () => {
    expect(defaultPourOz('WINE_RED')).toBe(5);
    expect(defaultPourOz('WINE_SPARKLING')).toBe(5);
  });

  it('defaults beer styles to a 12 oz serving', () => {
    expect(defaultPourOz('IPA')).toBe(12);
    expect(defaultPourOz('BEER')).toBe(12);
  });

  it('defaults spirits to a 1.5 oz standard pour', () => {
    expect(defaultPourOz('BOURBON')).toBe(1.5);
    expect(defaultPourOz('GIN')).toBe(1.5);
    expect(defaultPourOz('OTHER')).toBe(1.5);
  });
});
