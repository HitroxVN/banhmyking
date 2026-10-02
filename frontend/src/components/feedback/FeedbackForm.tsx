import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { MessageSquare, Send } from 'lucide-react';
import { feedbackApi } from '../../api/feedbackApi';
import { orderApi } from '../../api/orderApi';
import { storeApi } from '../../api/storeApi';
import { useAuth } from '../../context/useAuth';
import { Button, Input, Select, Textarea, useToast } from '../ui';
import { EMAIL_REGEX, FEEDBACK_TYPE_LABEL, VN_PHONE_REGEX, normalizePhone } from '../../utils/contentLabels';
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import type { FeedbackType } from '../../types/content';
import type { OrderResponse } from '../../types/order';
import type { PublicStore } from '../../types/store';
import '../../styles/components/content.css';

const FEEDBACK_TYPES: FeedbackType[] = ['SUGGESTION', 'COMPLAINT', 'PARTNERSHIP', 'OTHER'];
const RECENT_ORDERS = 20;

interface FormState {
  type: FeedbackType;
  storeId: number | null;
  orderCode: string;
  fullName: string;
  phone: string;
  email: string;
  subject: string;
  content: string;
  website: string;
}

type FormErrors = Partial<Record<keyof FormState, string>>;

const initialState = (orderCode: string): FormState => ({
  type: orderCode ? 'COMPLAINT' : 'SUGGESTION',
  storeId: null,
  orderCode,
  fullName: '',
  phone: '',
  email: '',
  subject: orderCode ? `Phản hồi về đơn ${orderCode}` : '',
  content: '',
  website: '',
});

/**
 * Form phản hồi (spec D §5). Khách đăng nhập chọn được đơn của mình → cơ sở tự điền theo đơn và bị khoá.
 * `?orderCode=` (từ trang theo dõi đơn) mở sẵn đơn đó.
 */
const FeedbackFormBody = () => {
  const { user, isAuthenticated } = useAuth();
  // R10: chỉ khách hàng (CUSTOMER) tra/chọn đơn của mình; vai trò khác bỏ qua ?orderCode=
  const isCustomer = isAuthenticated && user?.role === 'CUSTOMER';
  const toast = useToast();
  const [searchParams] = useSearchParams();
  const initialOrderCode = searchParams.get('orderCode')?.trim() ?? '';
  const sectionRef = useRef<HTMLElement>(null);

  const [stores, setStores] = useState<PublicStore[]>([]);
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [ordersLoaded, setOrdersLoaded] = useState(false);
  const [form, setForm] = useState<FormState>(() => ({
    ...initialState(initialOrderCode),
    fullName: user?.fullName ?? '',
    phone: user?.phone ?? '',
    email: user?.email ?? '',
  }));
  const [errors, setErrors] = useState<FormErrors>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [doneMessage, setDoneMessage] = useState<string | null>(null);

  useEffect(() => {
    if (initialOrderCode) sectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, [initialOrderCode]);

  useEffect(() => {
    let alive = true;
    storeApi
      .listPublic()
      .then((data) => {
        if (alive) setStores(data);
      })
      .catch(() => {
        if (alive) setStores([]);
      });
    return () => {
      alive = false;
    };
  }, []);

  // Đơn gần đây của khách (API đơn hàng hiện có) + đơn trong URL nếu nằm ngoài trang đầu
  useEffect(() => {
    if (!isCustomer) return;
    let alive = true;
    const load = async () => {
      try {
        const page = await orderApi.getUserOrders(0, RECENT_ORDERS);
        let list = page.content;
        if (initialOrderCode && !list.some((order) => order.orderCode === initialOrderCode)) {
          try {
            list = [await orderApi.getOrderByCode(initialOrderCode), ...list];
          } catch {
            // Không phải đơn của mình → bỏ qua, phản hồi gửi không kèm đơn
          }
        }
        if (alive) setOrders(list);
      } catch {
        if (alive) setOrders([]);
      } finally {
        if (alive) setOrdersLoaded(true);
      }
    };
    void load();
    return () => {
      alive = false;
    };
  }, [isCustomer, initialOrderCode]);

  const selectedOrder = orders.find((order) => order.orderCode === form.orderCode) ?? null;
  const storeValue = selectedOrder?.storeId ?? form.storeId;
  const orderNotFound = isCustomer && ordersLoaded && form.orderCode !== '' && selectedOrder === null;

  const setField = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const validate = (): boolean => {
    const next: FormErrors = {};
    if (!form.fullName.trim()) next.fullName = 'Vui lòng nhập họ tên';
    const phone = normalizePhone(form.phone);
    const email = form.email.trim();
    if (!phone && !email) next.phone = 'Vui lòng nhập số điện thoại hoặc email để cửa hàng liên hệ lại';
    if (phone && !VN_PHONE_REGEX.test(phone)) next.phone = 'Số điện thoại không đúng định dạng Việt Nam';
    if (email && !EMAIL_REGEX.test(email)) next.email = 'Email không đúng định dạng';
    if (!form.subject.trim()) next.subject = 'Vui lòng nhập tiêu đề';
    const contentLength = form.content.trim().length;
    if (contentLength < 10 || contentLength > 5000) next.content = 'Nội dung phải từ 10 đến 5000 ký tự';
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!validate()) return;
    setIsSubmitting(true);
    try {
      const message = await feedbackApi.submit({
        type: form.type,
        storeId: selectedOrder ? null : form.storeId,
        orderCode: selectedOrder ? selectedOrder.orderCode : null,
        fullName: form.fullName.trim(),
        phone: normalizePhone(form.phone) || null,
        email: form.email.trim() || null,
        subject: form.subject.trim(),
        content: form.content.trim(),
        website: form.website,
      });
      setDoneMessage(message);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Gửi phản hồi thất bại, vui lòng thử lại');
    } finally {
      setIsSubmitting(false);
    }
  };

  const resetForm = () => {
    setDoneMessage(null);
    setErrors({});
    setForm({
      ...initialState(''),
      fullName: user?.fullName ?? '',
      phone: user?.phone ?? '',
      email: user?.email ?? '',
    });
  };

  return (
    <section ref={sectionRef} id="feedback">
      <h2>
        <MessageSquare size={20} /> Gửi phản hồi cho cửa hàng
      </h2>

      {doneMessage ? (
        <div className="cf-form__success" role="status">
          <strong>{doneMessage}</strong>
          <span>Cửa hàng sẽ xem và liên hệ lại với bạn nếu cần.</span>
          <Button size="sm" variant="secondary" onClick={resetForm}>
            Gửi phản hồi khác
          </Button>
        </div>
      ) : (
        <form className="cf-form" onSubmit={(event) => void handleSubmit(event)} noValidate>
          <div className="cf-form__row">
            <Select label="Loại phản hồi" required value={form.type} onChange={(event) => setField('type', event.target.value as FeedbackType)}>
              {FEEDBACK_TYPES.map((type) => (
                <option key={type} value={type}>
                  {FEEDBACK_TYPE_LABEL[type]}
                </option>
              ))}
            </Select>
            <Select
              label="Cơ sở"
              value={storeValue == null ? '' : String(storeValue)}
              disabled={selectedOrder !== null}
              hint={selectedOrder ? 'Tự lấy theo cơ sở phục vụ đơn hàng' : undefined}
              onChange={(event) => setField('storeId', event.target.value ? Number(event.target.value) : null)}
            >
              <option value="">Chung toàn chuỗi</option>
              {selectedOrder?.storeId != null && !stores.some((store) => store.id === selectedOrder.storeId) && (
                <option value={selectedOrder.storeId}>{selectedOrder.storeName ?? 'Cơ sở của đơn'}</option>
              )}
              {stores.map((store) => (
                <option key={store.id} value={store.id}>
                  {store.name}
                </option>
              ))}
            </Select>
          </div>

          {isCustomer ? (
            <Select
              label="Đơn hàng liên quan"
              value={selectedOrder ? selectedOrder.orderCode : ''}
              onChange={(event) => setField('orderCode', event.target.value)}
              hint={orderNotFound ? `Không tìm thấy đơn ${form.orderCode} trong tài khoản của bạn — phản hồi sẽ gửi không kèm đơn.` : undefined}
            >
              <option value="">Không gắn đơn hàng</option>
              {orders.map((order) => (
                <option key={order.orderCode} value={order.orderCode}>
                  {order.orderCode} · {formatDateTime(order.createdAt)} · {formatCurrency(order.total)}
                </option>
              ))}
            </Select>
          ) : (
            !isAuthenticated &&
            form.orderCode && (
              <p className="cf-form__note">
                <Link to="/login">Đăng nhập</Link> để gắn phản hồi với đơn {form.orderCode}.
              </p>
            )
          )}

          <div className="cf-form__row">
            <Input label="Họ và tên" required maxLength={100} value={form.fullName} onChange={(event) => setField('fullName', event.target.value)} error={errors.fullName} />
            <Input label="Số điện thoại" type="tel" maxLength={20} value={form.phone} onChange={(event) => setField('phone', event.target.value)} error={errors.phone} />
          </div>
          <Input label="Email" type="email" maxLength={150} value={form.email} onChange={(event) => setField('email', event.target.value)} error={errors.email} hint="Cần ít nhất số điện thoại hoặc email." />
          <Input label="Tiêu đề" required maxLength={200} value={form.subject} onChange={(event) => setField('subject', event.target.value)} error={errors.subject} />
          <Textarea
            label="Nội dung"
            required
            rows={6}
            maxLength={5000}
            value={form.content}
            onChange={(event) => setField('content', event.target.value)}
            error={errors.content}
            hint={`${form.content.trim().length}/5000 ký tự (tối thiểu 10)`}
          />

          {/* Ô bẫy bot — người dùng không thấy */}
          <div className="hp-field" aria-hidden="true">
            <label>
              Website
              <input type="text" name="website" tabIndex={-1} autoComplete="off" value={form.website} onChange={(event) => setField('website', event.target.value)} />
            </label>
          </div>

          <Button type="submit" loading={isSubmitting} icon={<Send size={16} />}>
            Gửi phản hồi
          </Button>
        </form>
      )}
    </section>
  );
};

/** Đổi tài khoản (hoặc user vừa tải xong) thì dựng lại form để điền sẵn họ tên/SĐT/email. */
export const FeedbackForm = () => {
  const { user } = useAuth();
  return <FeedbackFormBody key={user?.id ?? 'guest'} />;
};
