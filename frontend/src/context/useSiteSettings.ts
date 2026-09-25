import { useContext } from 'react';
import { SiteSettingsContext } from './siteSettingsContextDef';
import type { SiteSettingsContextType } from './siteSettingsContextDef';

export const useSiteSettings = (): SiteSettingsContextType => {
  const context = useContext(SiteSettingsContext);
  if (!context) {
    throw new Error('useSiteSettings must be used within a SiteSettingsProvider');
  }
  return context;
};
