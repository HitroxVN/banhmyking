import { createContext } from 'react';
import type { RealtimeMessage, RealtimeStatus } from '../realtime/types';

export type RealtimeListener = (message: RealtimeMessage) => void;

export interface RealtimeContextType {
  status: RealtimeStatus;
  subscribe: (listener: RealtimeListener) => () => void;
}

/** Mặc định (ngoài provider): ngoại tuyến, không phát gì — trang tự rơi về polling dự phòng */
export const RealtimeContext = createContext<RealtimeContextType>({
  status: 'offline',
  subscribe: () => () => {},
});
