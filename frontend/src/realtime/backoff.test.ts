import { describe, expect, it } from 'vitest';
import { backoffDelay } from './backoff';

const noJitter = () => 0.5; // 0.5 → hệ số 1.0

describe('backoffDelay', () => {
  it('theo lịch 1s → 2s → 5s → 10s → 30s', () => {
    expect([0, 1, 2, 3, 4].map((attempt) => backoffDelay(attempt, noJitter))).toEqual([1000, 2000, 5000, 10000, 30000]);
  });

  it('giữ 30s cho mọi lần sau', () => {
    expect(backoffDelay(9, noJitter)).toBe(30000);
  });

  it('jitter nằm trong ±20%', () => {
    expect(backoffDelay(0, () => 0)).toBe(800);
    expect(backoffDelay(0, () => 1)).toBe(1200);
  });
});
