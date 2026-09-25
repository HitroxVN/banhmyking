import React, { useCallback, useEffect, useState } from 'react';
import { siteSettingsApi } from '../api/siteSettingsApi';
import { DEFAULT_SITE_SETTINGS } from '../types/siteSettings';
import type { SiteSettings } from '../types/siteSettings';
import { SiteSettingsContext } from './siteSettingsContextDef';

export const SiteSettingsProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [settings, setSettings] = useState<SiteSettings>(DEFAULT_SITE_SETTINGS);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  const refresh = useCallback(async () => {
    try {
      const fetched = await siteSettingsApi.getPublic();
      // Trộn lên bản mặc định: nếu backend chưa có key nào đó thì vẫn có chữ để hiện,
      // thay vì render "undefined" ra giữa trang.
      setSettings({ ...DEFAULT_SITE_SETTINGS, ...fetched });
    } catch (error) {
      // API chết thì giữ bản mặc định — site không được trắng tên hay vỡ banner vì lỗi mạng
      console.error('Không tải được cấu hình trang web, tạm dùng giá trị mặc định:', error);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  useEffect(() => {
    document.title = `${settings.siteName} — ${settings.tagline}`;
  }, [settings.siteName, settings.tagline]);

  return (
    <SiteSettingsContext.Provider value={{ settings, isLoading, refresh }}>
      {children}
    </SiteSettingsContext.Provider>
  );
};
