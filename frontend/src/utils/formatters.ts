/**
 * Định dạng số tiền sang định dạng tiền tệ Việt Nam (VND)
 * Ví dụ: 30000 -> "30.000 đ"
 */
export const formatCurrency = (amount: number | string | null | undefined): string => {
  if (amount === null || amount === undefined || isNaN(Number(amount))) {
    return '0 đ';
  }
  const numericAmount = Math.round(Number(amount));
  return `${numericAmount.toLocaleString('vi-VN')} đ`;
};

/**
 * Định dạng ngày giờ dạng chuẩn Việt Nam: "HH:mm DD/MM/YYYY"
 */
export const formatDateTime = (dateStr: string | null | undefined): string => {
  if (!dateStr) return '--:--';
  try {
    const d = new Date(dateStr);
    return d.toLocaleString('vi-VN', {
      hour: '2-digit',
      minute: '2-digit',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });
  } catch {
    return dateStr;
  }
};

/**
 * Định dạng ngày dạng chuẩn Việt Nam: "DD/MM/YYYY"
 */
export const formatDate = (dateStr: string | null | undefined): string => {
  if (!dateStr) return '--/--/----';
  try {
    const d = new Date(dateStr);
    return d.toLocaleDateString('vi-VN', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });
  } catch {
    return dateStr;
  }
};

