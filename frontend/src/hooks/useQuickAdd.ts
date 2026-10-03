import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useToast } from '../components/ui';
import { useAuth } from '../context/useAuth';
import { useCart } from '../context/useCart';
import type { ProductItem } from '../types/staff';
import { flyToCart } from '../utils/flyToCart';

/**
 * Thêm nhanh một món vào giỏ từ thẻ sản phẩm.
 *
 * <p>Trang thực đơn giờ mở cho cả khách chưa đăng nhập, nên gọi API giỏ hàng sẽ ăn 401
 * rồi bị interceptor kéo về /login mất ngữ cảnh. Thay vào đó đưa thẳng sang trang đăng
 * nhập kèm `from` để quay lại đúng chỗ vừa bấm.
 */
export const useQuickAdd = () => {
  const [quickAddingId, setQuickAddingId] = useState<number | null>(null);
  const { addItem } = useCart();
  const { isAuthenticated } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();

  /** `source` là nút vừa bấm — làm điểm xuất phát cho ổ bánh bay vào giỏ */
  const quickAdd = async (product: ProductItem, source?: Element | null) => {
    if (!isAuthenticated) {
      toast.info('Đăng nhập để thêm món vào giỏ');
      navigate('/login', { state: { from: location } });
      return;
    }

    setQuickAddingId(product.id);
    try {
      await addItem(product.id, 1);
      flyToCart(source);
      toast.success(`Đã thêm ${product.name} vào giỏ`);
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Thêm món vào giỏ thất bại');
    } finally {
      setQuickAddingId(null);
    }
  };

  return { quickAddingId, quickAdd };
};
