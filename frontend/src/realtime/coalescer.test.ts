import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createCoalescer } from './coalescer';

describe('createCoalescer', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('gộp nhiều lần trigger trong khoảng chờ thành một lần chạy', () => {
    const fn = vi.fn();
    const coalescer = createCoalescer(fn, 300);
    coalescer.trigger();
    coalescer.trigger();
    vi.advanceTimersByTime(299);
    coalescer.trigger();
    expect(fn).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it('trigger sau khi đã chạy thì mở lượt mới', () => {
    const fn = vi.fn();
    const coalescer = createCoalescer(fn, 300);
    coalescer.trigger();
    vi.advanceTimersByTime(300);
    coalescer.trigger();
    vi.advanceTimersByTime(300);
    expect(fn).toHaveBeenCalledTimes(2);
  });

  it('cancel huỷ lượt đang chờ', () => {
    const fn = vi.fn();
    const coalescer = createCoalescer(fn, 300);
    coalescer.trigger();
    coalescer.cancel();
    vi.advanceTimersByTime(1000);
    expect(fn).not.toHaveBeenCalled();
  });
});
