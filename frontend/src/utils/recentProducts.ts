/** Số món nhớ lại — đủ để gợi ý, không cần lưu lịch sử dài */
const MAX_RECENT = 8;
const STORAGE_KEY = 'bmk_recent_products';

/** Danh sách id món đã xem, mới nhất đứng đầu. Lỗi/định dạng lạ đều trả về rỗng. */
export const readRecentProducts = (): number[] => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const parsed: unknown = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed.filter((id): id is number => Number.isInteger(id)) : [];
  } catch {
    return [];
  }
};

/**
 * Ghi nhớ một món vừa xem. Đây là tiện ích phía trình duyệt nên mọi lỗi lưu trữ
 * (chế độ riêng tư, quota) đều bỏ qua im lặng — không được làm hỏng trang chi tiết.
 */
export const rememberProduct = (productId: number): void => {
  try {
    const next = readRecentProducts().filter((id) => id !== productId);
    next.unshift(productId);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next.slice(0, MAX_RECENT)));
  } catch {
    // Không lưu được thì thôi
  }
};
