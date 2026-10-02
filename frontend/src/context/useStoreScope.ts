import { useContext } from 'react';
import { StoreScopeContext, type StoreScopeContextType } from './storeScopeContextDef';

export const useStoreScope = (): StoreScopeContextType => {
  const context = useContext(StoreScopeContext);
  if (!context) throw new Error('useStoreScope must be used within a StoreScopeProvider');
  return context;
};
