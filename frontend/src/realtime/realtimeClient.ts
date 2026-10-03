import { authApi } from '../api/authApi';
import { tokenStorage } from '../utils/tokenStorage';
import { backoffDelay, STABLE_AFTER_MS } from './backoff';
import { createSseParser, type SseMessage } from './sseParser';
import type { RealtimeMessage, RealtimeStatus } from './types';

/** Server ping mỗi 25s; quá ngần này không nhận byte nào thì coi là kết nối treo (half-open). */
const IDLE_TIMEOUT_MS = 60_000;

const STREAM_URL = `${import.meta.env.VITE_API_BASE_URL || '/api/v1'}/realtime/stream`;

export interface RealtimeClientOptions {
  onMessage: (message: RealtimeMessage) => void;
  onStatus: (status: RealtimeStatus) => void;
}

const toMessage = ({ event, data }: SseMessage): RealtimeMessage | null => {
  if (event === 'ready') return { type: 'ready' };
  if (event !== 'order' && event !== 'inbox') return null;
  try {
    return { type: event, payload: JSON.parse(data) } as RealtimeMessage;
  } catch {
    return null;
  }
};

/**
 * Mở luồng SSE bằng fetch (EventSource không gửi được header Authorization), tự nối lại theo backoff.
 * 401: để interceptor axios làm mới token qua getMe() rồi nối lại; làm mới hỏng thì dừng — luồng đăng
 * xuất sẵn có lo phần còn lại. Trả hàm dừng (gọi khi đăng xuất / unmount).
 */
export const startRealtimeClient = ({ onMessage, onStatus }: RealtimeClientOptions): (() => void) => {
  let stopped = false;
  let attempt = 0;
  let controller: AbortController | null = null;
  let retryTimer: number | null = null;
  let idleTimer: number | null = null;

  const clearIdle = () => {
    if (idleTimer !== null) window.clearTimeout(idleTimer);
    idleTimer = null;
  };
  // Đặt lại đồng hồ chờ mỗi lần có dữ liệu; hết hạn thì huỷ để vòng đọc thoát và nối lại
  const armIdle = (current: AbortController) => {
    clearIdle();
    idleTimer = window.setTimeout(() => current.abort(), IDLE_TIMEOUT_MS);
  };

  const scheduleRetry = () => {
    if (stopped) return;
    onStatus('reconnecting');
    retryTimer = window.setTimeout(() => void connect(), backoffDelay(attempt));
    attempt += 1;
  };

  const connect = async () => {
    if (stopped) return;
    const token = tokenStorage.getAccessToken();
    if (!token) {
      onStatus('offline');
      return;
    }

    controller = new AbortController();
    const openedAt = Date.now();
    try {
      const response = await fetch(STREAM_URL, {
        headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
        signal: controller.signal,
        cache: 'no-store',
      });

      if (response.status === 401) {
        try {
          await authApi.getMe();
        } catch {
          onStatus('offline');
          return;
        }
        scheduleRetry();
        return;
      }
      if (!response.ok || !response.body) {
        scheduleRetry();
        return;
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      const parse = createSseParser();
      armIdle(controller);
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        armIdle(controller);
        for (const raw of parse(decoder.decode(value, { stream: true }))) {
          const message = toMessage(raw);
          if (!message) continue;
          if (message.type === 'ready') onStatus('live');
          onMessage(message);
        }
      }
    } catch {
      // abort khi dừng, hoặc mất mạng giữa chừng — xử lý chung bên dưới
    }
    clearIdle();

    if (stopped) return;
    if (Date.now() - openedAt >= STABLE_AFTER_MS) attempt = 0;
    scheduleRetry();
  };

  void connect();

  return () => {
    stopped = true;
    clearIdle();
    controller?.abort();
    if (retryTimer !== null) window.clearTimeout(retryTimer);
  };
};
