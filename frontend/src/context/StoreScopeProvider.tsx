import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { storeApi } from '../api/storeApi';
import { useAuth } from './useAuth';
import { StoreScopeContext } from './storeScopeContextDef';

const STORAGE_KEY = 'bmk_admin_store_scope';

const readSaved = (): { id: number | null; name: string | null } => {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as { id: number | null; name: string | null }) : { id: null, name: null };
  } catch {
    return { id: null, name: null };
  }
};

/** Cơ sở đang làm việc của khu /staff và /shipper */
export const StoreScopeProvider = ({ children }: { children: ReactNode }) => {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const [adminChoice, setAdminChoice] = useState(readSaved);

  // Cơ sở đã lưu có thể đã bị xoá: đối chiếu với danh sách thật, không còn thì về "chưa chọn".
  // Chỉ kiểm một lần mỗi lần đăng nhập (theo user) — lựa chọn mới trong phiên lấy từ danh sách
  // vừa tải nên không cần gọi lại GET /admin/stores mỗi lần ADMIN đổi cơ sở.
  const userId = user?.id ?? null;
  useEffect(() => {
    if (!isAdmin || userId == null) return;
    const savedId = readSaved().id;
    if (savedId == null) return;
    let alive = true;
    storeApi
      .adminList()
      .then((stores) => {
        if (!alive || stores.some((s) => s.id === savedId)) return;
        // ADMIN có thể đã chọn cơ sở khác trong lúc chờ — chỉ dọn khi vẫn là lựa chọn cũ
        setAdminChoice((prev) => {
          if (prev.id !== savedId) return prev;
          try {
            window.localStorage.removeItem(STORAGE_KEY);
          } catch {
            // Bỏ qua — chỉ là dọn dữ liệu cũ
          }
          return { id: null, name: null };
        });
      })
      .catch(() => {
        // Không tải được danh sách thì giữ nguyên lựa chọn đã lưu
      });
    return () => {
      alive = false;
    };
  }, [isAdmin, userId]);

  const value = useMemo(
    () => ({
      storeId: isAdmin ? adminChoice.id : user?.storeId ?? null,
      storeName: isAdmin ? adminChoice.name : user?.storeName ?? null,
      canChoose: isAdmin,
      setStore: (id: number | null, name: string | null) => {
        if (!isAdmin) return;
        setAdminChoice({ id, name });
        try {
          window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ id, name }));
        } catch {
          // Chế độ riêng tư chặn localStorage — vẫn dùng được trong phiên
        }
      },
    }),
    [isAdmin, adminChoice, user?.storeId, user?.storeName]
  );

  return <StoreScopeContext.Provider value={value}>{children}</StoreScopeContext.Provider>;
};
