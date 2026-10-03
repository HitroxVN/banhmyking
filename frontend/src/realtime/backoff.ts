const STEPS_MS = [1000, 2000, 5000, 10000, 30000];

/** Giữ được kết nối lâu hơn ngần này thì lần mất kết nối sau bắt đầu lại từ 1 giây */
export const STABLE_AFTER_MS = 60_000;

/**
 * Thời gian chờ trước lần nối lại thứ `attempt` (0 = lần đầu thất bại), cộng ngẫu nhiên ±20%
 * để nhiều tab không cùng dội vào server một lúc khi backend khởi động lại.
 */
export const backoffDelay = (attempt: number, random: () => number = Math.random): number => {
  const base = STEPS_MS[Math.min(Math.max(attempt, 0), STEPS_MS.length - 1)];
  return Math.round(base * (0.8 + random() * 0.4));
};
