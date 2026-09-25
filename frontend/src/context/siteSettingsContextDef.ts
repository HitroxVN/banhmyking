import { createContext } from 'react';
import type { SiteSettings } from '../types/siteSettings';

export interface SiteSettingsContextType {
  /** Luôn đủ mọi field: chưa tải xong hoặc API lỗi thì là giá trị mặc định. */
  settings: SiteSettings;
  isLoading: boolean;
  /** Tải lại cấu hình — trang admin gọi sau khi lưu để mọi chỗ đang hiện cập nhật theo. */
  refresh: () => Promise<void>;
}

export const SiteSettingsContext = createContext<SiteSettingsContextType | undefined>(undefined);
