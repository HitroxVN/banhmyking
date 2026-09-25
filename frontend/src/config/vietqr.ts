/**
 * Cấu hình thông tin thanh toán VietQR
 * Các thành viên trong nhóm có thể dễ dàng sửa đổi thông tin tài khoản ngân hàng
 * bằng cách cập nhật các biến môi trường trong file .env tại thư mục frontend/:
 *
 * VIETQR_BANK_ID=techcombank
 * VIETQR_ACCOUNT_NO=8888332999
 * VIETQR_ACCOUNT_NAME="DAO NGOC THINH"
 * VIETQR_TEMPLATE=compact2
 */

// Hàm loại bỏ dấu ngoặc kép thừa nếu user nhập "DAO NGOC THINH"
const cleanString = (val: string | undefined, fallback: string): string => {
  if (!val) return fallback;
  return val.replace(/^["']|["']$/g, '').trim();
};

export const VIETQR_CONFIG = {
  bankId: cleanString(
    import.meta.env.VIETQR_BANK_ID || import.meta.env.VITE_VIETQR_BANK_ID,
    'techcombank'
  ),
  accountNo: cleanString(
    import.meta.env.VIETQR_ACCOUNT_NO || import.meta.env.VITE_VIETQR_ACCOUNT_NO,
    '8888332999'
  ),
  accountName: cleanString(
    import.meta.env.VIETQR_ACCOUNT_NAME || import.meta.env.VITE_VIETQR_ACCOUNT_NAME,
    'DAO NGOC THINH'
  ),
  template: cleanString(
    import.meta.env.VIETQR_TEMPLATE || import.meta.env.VITE_VIETQR_TEMPLATE,
    'compact2'
  ),
};

/**
 * Tạo URL mã QR thanh toán từ dịch vụ VietQR API
 * Cú pháp chuẩn VietQR:
 * https://img.vietqr.io/image/<BANK_ID>-<ACCOUNT_NO>-<TEMPLATE>.png?amount=<AMOUNT>&addInfo=<ORDER_CODE>&accountName=<ACCOUNT_NAME>
 */
export const generateVietQrUrl = (amount: number, orderCode: string): string => {
  const { bankId, accountNo, template, accountName } = VIETQR_CONFIG;
  const encodedAccountName = encodeURIComponent(accountName);
  const encodedOrderCode = encodeURIComponent(orderCode);

  return `https://img.vietqr.io/image/${bankId}-${accountNo}-${template}.png?amount=${amount}&addInfo=${encodedOrderCode}&accountName=${encodedAccountName}`;
};
