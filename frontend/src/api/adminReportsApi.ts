import { axiosClient } from './axiosClient';
import type { ApiResponse } from '../types/auth';
import type { PriceSavings, ReportFilterParams, ReportType, TopProduct } from '../types/admin';
import type { StoreRevenue } from '../types/store';

/** Bóc tên file từ `Content-Disposition: attachment; filename="..."`. */
const readFileName = (disposition: unknown, fallback: string): string => {
  const match = typeof disposition === 'string' ? disposition.match(/filename="?([^";]+)"?/) : null;
  return match?.[1] ?? fallback;
};

const saveBlob = (blob: Blob, fileName: string) => {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
};

export const adminReportsApi = {
  /** Bảng món bán chạy trong khoảng ngày (mặc định 30 ngày gần nhất) */
  async getTopProducts(params: ReportFilterParams = {}): Promise<TopProduct[]> {
    const res = await axiosClient.get<ApiResponse<TopProduct[]>>('/admin/reports/top-products', { params });
    return res.data.data;
  },

  async getRevenueByStore(params: ReportFilterParams = {}): Promise<StoreRevenue[]> {
    const res = await axiosClient.get<ApiResponse<StoreRevenue[]>>('/admin/reports/revenue-by-store', { params });
    return res.data.data;
  },

  /** Tiền ưu đãi từ giá KM và combo (đơn đã giao) */
  async getPriceSavings(params: ReportFilterParams = {}): Promise<PriceSavings> {
    const res = await axiosClient.get<ApiResponse<PriceSavings>>('/admin/reports/price-savings', { params });
    return res.data.data;
  },

  /**
   * Tải file CSV. Endpoint trả bytes thô (không bọc ApiResponse) nên phải đọc dạng blob
   * thay vì dùng chung đường `res.data.data` như các API khác.
   */
  async downloadCsv(type: ReportType, params: ReportFilterParams = {}): Promise<void> {
    const res = await axiosClient.get<Blob>('/admin/reports/export', {
      params: { type, ...params },
      responseType: 'blob',
    });
    saveBlob(res.data, readFileName(res.headers['content-disposition'], `${type.toLowerCase()}.csv`));
  },
};
