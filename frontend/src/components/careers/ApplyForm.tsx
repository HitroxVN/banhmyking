import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { Send } from 'lucide-react';
import { jobApi } from '../../api/jobApi';
import { useAuth } from '../../context/useAuth';
import { Button, Input, Select, Textarea, useToast } from '../ui';
import { EMAIL_REGEX, VN_PHONE_REGEX, normalizePhone } from '../../utils/contentLabels';
import type { ApplicationForm, JobDetail } from '../../types/content';
import '../../styles/components/content.css';

const MAX_CV_MB = 5;
const CV_EXTENSIONS = ['pdf', 'jpg', 'jpeg', 'png'];

type FormErrors = Partial<Record<keyof ApplicationForm | 'cv', string>>;

export interface ApplyFormProps {
  job: JobDetail;
}

/** Đổi tài khoản (hoặc user vừa tải xong) thì dựng lại form để điền sẵn họ tên/SĐT/email. */
export const ApplyForm = ({ job }: ApplyFormProps) => {
  const { user } = useAuth();
  return <ApplyFormBody key={user?.id ?? 'guest'} job={job} />;
};

/** Form ứng tuyển công khai (spec D2): CV không bắt buộc, có ô bẫy bot ẩn "website". */
const ApplyFormBody = ({ job }: ApplyFormProps) => {
  const { user } = useAuth();
  const toast = useToast();
  const [form, setForm] = useState<ApplicationForm>(() => ({
    storeId: job.stores.length === 1 ? job.stores[0].id : null,
    fullName: user?.fullName ?? '',
    phone: user?.phone ?? '',
    email: user?.email ?? '',
    message: '',
    website: '',
  }));
  const [cv, setCv] = useState<File | null>(null);
  const [errors, setErrors] = useState<FormErrors>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [doneMessage, setDoneMessage] = useState<string | null>(null);

  const setField = <K extends keyof ApplicationForm>(key: K, value: ApplicationForm[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const validate = (): boolean => {
    const next: FormErrors = {};
    if (form.storeId == null) next.storeId = 'Vui lòng chọn cơ sở muốn làm việc';
    if (!form.fullName.trim()) next.fullName = 'Vui lòng nhập họ tên';
    const phone = normalizePhone(form.phone);
    if (!phone) next.phone = 'Vui lòng nhập số điện thoại';
    else if (!VN_PHONE_REGEX.test(phone)) next.phone = 'Số điện thoại không đúng định dạng Việt Nam';
    if (form.email.trim() && !EMAIL_REGEX.test(form.email.trim())) next.email = 'Email không đúng định dạng';
    if (cv) {
      const ext = cv.name.split('.').pop()?.toLowerCase() ?? '';
      if (!CV_EXTENSIONS.includes(ext)) next.cv = 'File CV phải là PDF, JPG hoặc PNG';
      else if (cv.size > MAX_CV_MB * 1024 * 1024) next.cv = 'File CV không được vượt quá 5MB';
    }
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!validate()) return;
    setIsSubmitting(true);
    try {
      const message = await jobApi.apply(
        job.slug,
        {
          ...form,
          fullName: form.fullName.trim(),
          phone: normalizePhone(form.phone),
          email: form.email.trim(),
          message: form.message.trim(),
        },
        cv
      );
      setDoneMessage(message);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Gửi hồ sơ thất bại, vui lòng thử lại');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (doneMessage) {
    return (
      <div className="cf-form__success" role="status">
        <strong>{doneMessage}</strong>
        <span>Nếu bạn có để lại email, cửa hàng đã gửi thư xác nhận.</span>
        <Link className="ui-btn ui-btn--secondary ui-btn--sm" to="/tuyen-dung">
          Xem vị trí khác
        </Link>
      </div>
    );
  }

  return (
    <form className="cf-form" onSubmit={(event) => void handleSubmit(event)} noValidate>
      <Select
        label="Cơ sở muốn làm việc"
        required
        value={form.storeId == null ? '' : String(form.storeId)}
        onChange={(event) => setField('storeId', event.target.value ? Number(event.target.value) : null)}
        error={errors.storeId}
      >
        <option value="">— Chọn cơ sở —</option>
        {job.stores.map((store) => (
          <option key={store.id} value={store.id}>
            {store.name}
          </option>
        ))}
      </Select>

      <div className="cf-form__row">
        <Input
          label="Họ và tên"
          required
          maxLength={100}
          value={form.fullName}
          onChange={(event) => setField('fullName', event.target.value)}
          error={errors.fullName}
        />
        <Input
          label="Số điện thoại"
          required
          type="tel"
          maxLength={20}
          value={form.phone}
          onChange={(event) => setField('phone', event.target.value)}
          error={errors.phone}
        />
      </div>

      <Input
        label="Email (để nhận thư xác nhận)"
        type="email"
        maxLength={150}
        value={form.email}
        onChange={(event) => setField('email', event.target.value)}
        error={errors.email}
      />

      <Textarea
        label="Giới thiệu ngắn / lời nhắn"
        rows={4}
        maxLength={2000}
        value={form.message}
        onChange={(event) => setField('message', event.target.value)}
        hint="Ca làm mong muốn, kinh nghiệm, thời gian có thể bắt đầu…"
      />

      <label className="ui-field">
        <span className="ui-field__label">CV (không bắt buộc)</span>
        <input
          type="file"
          accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
          aria-describedby="apply-cv-msg"
          aria-invalid={errors.cv ? true : undefined}
          onChange={(event) => setCv(event.target.files?.[0] ?? null)}
        />
        <span id="apply-cv-msg" className={`ui-field__msg${errors.cv ? ' ui-field__msg--error' : ''}`}>
          {errors.cv ?? 'PDF, JPG hoặc PNG, tối đa 5MB.'}
        </span>
      </label>

      {/* Ô bẫy bot — người dùng không thấy; có giá trị thì backend trả 200 giả và không lưu */}
      <div className="hp-field" aria-hidden="true">
        <label>
          Website
          <input
            type="text"
            name="website"
            tabIndex={-1}
            autoComplete="off"
            value={form.website}
            onChange={(event) => setField('website', event.target.value)}
          />
        </label>
      </div>

      <p className="cf-form__note">Thông tin của bạn chỉ dùng cho việc tuyển dụng của cửa hàng.</p>
      <Button type="submit" loading={isSubmitting} icon={<Send size={16} />}>
        Gửi hồ sơ
      </Button>
    </form>
  );
};
