import { useCallback, useEffect, useState } from 'react';
import { feedbackApi } from '../api/feedbackApi';
import { jobApi } from '../api/jobApi';
import { INBOX_CHANGED_EVENT } from '../utils/inboxEvents';
import { usePolling } from './usePolling';
import type { NavBadge } from '../components/layout/navItems';

const POLL_MS = 60_000;

export type InboxCounts = Record<NavBadge, number>;

/**
 * Số hồ sơ ứng tuyển / phản hồi NEW trong phạm vi người dùng (MANAGER: cơ sở mình; ADMIN: toàn chuỗi).
 * Lỗi một API thì giữ số cũ của API đó, không làm hỏng menu.
 */
export const useInboxCounts = (enabled: boolean): InboxCounts => {
  const [counts, setCounts] = useState<InboxCounts>({ applications: 0, feedbacks: 0 });

  const refresh = useCallback(async () => {
    const [applications, feedbacks] = await Promise.allSettled([
      jobApi.countNewApplications(),
      feedbackApi.countNew(),
    ]);
    setCounts((prev) => ({
      applications: applications.status === 'fulfilled' ? applications.value : prev.applications,
      feedbacks: feedbacks.status === 'fulfilled' ? feedbacks.value : prev.feedbacks,
    }));
  }, []);

  useEffect(() => {
    if (!enabled) return;
    // Hỏi lần đầu qua timeout 0 để không setState đồng bộ trong effect
    const initial = window.setTimeout(() => void refresh(), 0);
    const handleChanged = () => {
      void refresh();
    };
    window.addEventListener(INBOX_CHANGED_EVENT, handleChanged);
    return () => {
      window.clearTimeout(initial);
      window.removeEventListener(INBOX_CHANGED_EVENT, handleChanged);
    };
  }, [enabled, refresh]);

  usePolling(refresh, { intervalMs: POLL_MS, enabled });

  return counts;
};
