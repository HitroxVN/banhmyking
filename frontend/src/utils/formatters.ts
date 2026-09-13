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
