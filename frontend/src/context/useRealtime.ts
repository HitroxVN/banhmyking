import { useContext, useEffect, useRef } from 'react';
import type { RealtimeStatus } from '../realtime/types';
import { RealtimeContext, type RealtimeListener } from './realtimeContextDef';

export const useRealtimeStatus = (): RealtimeStatus => useContext(RealtimeContext).status;

/** Nghe mọi tin realtime. `listener` đọc qua ref nên truyền hàm inline thoải mái. */
export const useRealtime = (listener: RealtimeListener) => {
  const { subscribe } = useContext(RealtimeContext);
  const listenerRef = useRef(listener);
  useEffect(() => {
    listenerRef.current = listener;
  });
  useEffect(() => subscribe((message) => listenerRef.current(message)), [subscribe]);
};
