import { useEffect, useRef } from 'react';
import { useRealtime, useRealtimeStatus } from '../context/useRealtime';
import { createCoalescer } from '../realtime/coalescer';
import type { OrderSignal } from '../realtime/types';
import { orderSyncChannel } from '../utils/orderSyncChannel';

const COALESCE_MS = 300;

interface LiveRefreshOptions {
  /** Tin đơn nào làm trang tải lại; bỏ trống = mọi tin đơn hub đã gửi cho người này */
  matchOrder?: (signal: OrderSignal) => boolean;
  /** true = nghe tin hộp thư thay vì tin đơn */
  inbox?: boolean;
  /** Chu kỳ polling dự phòng, chỉ chạy khi luồng realtime không "live" */
  fallbackMs?: number;
  enabled?: boolean;
}

/**
 * Thay polling cũ (spec realtime §5.2): tải lại khi có tín hiệu realtime khớp, khi nối lại (`resync`),
 * khi quay lại tab / focus, khi tab khác cùng trình duyệt báo qua BroadcastChannel; polling `fallbackMs`
 * chỉ khi mất luồng. Mọi nguồn đi qua một bộ gộp 300ms.
 */
export const useLiveRefresh = (
  refresh: () => void | Promise<void>,
  { matchOrder, inbox = false, fallbackMs = 30_000, enabled = true }: LiveRefreshOptions = {}
) => {
  const status = useRealtimeStatus();
  const refreshRef = useRef(refresh);
  const matchRef = useRef(matchOrder);
  const inboxRef = useRef(inbox);
  useEffect(() => {
    refreshRef.current = refresh;
    matchRef.current = matchOrder;
    inboxRef.current = inbox;
  });

  const coalescerRef = useRef<ReturnType<typeof createCoalescer> | null>(null);
  useEffect(() => {
    if (!enabled) return;
    const coalescer = createCoalescer(() => void refreshRef.current(), COALESCE_MS);
    coalescerRef.current = coalescer;
    return () => {
      coalescer.cancel();
      coalescerRef.current = null;
    };
  }, [enabled]);

  useRealtime((message) => {
    const coalescer = coalescerRef.current;
    if (!coalescer) return;
    if (message.type === 'resync') coalescer.trigger();
    else if (message.type === 'inbox' && inboxRef.current) coalescer.trigger();
    else if (message.type === 'order' && !inboxRef.current && (!matchRef.current || matchRef.current(message.payload))) {
      coalescer.trigger();
    }
  });

  useEffect(() => {
    if (!enabled) return;
    const run = () => coalescerRef.current?.trigger();
    const handleVisibility = () => {
      if (document.visibilityState === 'visible') run();
    };
    document.addEventListener('visibilitychange', handleVisibility);
    window.addEventListener('focus', run);
    orderSyncChannel?.addEventListener('message', run);
    // Polling dự phòng chỉ khi mất luồng realtime; tab ẩn thì bỏ lượt (quay lại tab sẽ tải ngay)
    const intervalId =
      status === 'live'
        ? null
        : window.setInterval(() => {
            if (document.visibilityState === 'visible') run();
          }, fallbackMs);

    return () => {
      document.removeEventListener('visibilitychange', handleVisibility);
      window.removeEventListener('focus', run);
      orderSyncChannel?.removeEventListener('message', run);
      if (intervalId !== null) window.clearInterval(intervalId);
    };
  }, [enabled, status, fallbackMs]);
};
