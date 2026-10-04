import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { startRealtimeClient } from '../realtime/realtimeClient';
import type { RealtimeStatus } from '../realtime/types';
import { RealtimeContext, type RealtimeListener } from './realtimeContextDef';
import { useAuth } from './useAuth';

/**
 * Một luồng realtime cho cả app (spec realtime §5.2): mở khi đã đăng nhập, đóng khi đăng xuất.
 * Nhận lại `ready` sau khi mất kết nối → phát `resync` để mọi trang đang mở tải bù một lần.
 */
export const RealtimeProvider = ({ children }: { children: ReactNode }) => {
  const { isAuthenticated } = useAuth();
  const [status, setStatus] = useState<RealtimeStatus>('offline');
  const listenersRef = useRef(new Set<RealtimeListener>());

  const subscribe = useCallback((listener: RealtimeListener) => {
    listenersRef.current.add(listener);
    return () => {
      listenersRef.current.delete(listener);
    };
  }, []);

  useEffect(() => {
    if (!isAuthenticated) return;
    let readyCount = 0;
    const emit: RealtimeListener = (message) => {
      // Mỗi listener tự cô lập: một nơi lỗi không làm các nơi khác mất tin hay làm đứt vòng đọc
      listenersRef.current.forEach((listener) => {
        try {
          listener(message);
        } catch (error) {
          console.error('Realtime listener lỗi', error);
        }
      });
    };

    return startRealtimeClient({
      onStatus: setStatus,
      onMessage: (message) => {
        if (message.type === 'ready') {
          readyCount += 1;
          if (readyCount > 1) emit({ type: 'resync' });
          return;
        }
        emit(message);
      },
    });
  }, [isAuthenticated]);

  const value = useMemo(
    () => ({ status: isAuthenticated ? status : ('offline' as const), subscribe }),
    [isAuthenticated, status, subscribe]
  );

  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>;
};
