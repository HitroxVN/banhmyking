import { useRef, useState } from 'react';
import { Image as ImageIcon, MapPinOff, RotateCcw, Save, Trash2, Upload, XCircle } from 'lucide-react';
import { siteSettingsApi } from '../../api/siteSettingsApi';
import { Button, Input, PageHeader, Skeleton, Textarea, useConfirm, useToast } from '../../components/ui';
import { useSiteSettings } from '../../context/useSiteSettings';
import type { SiteSettings } from '../../types/siteSettings';
import { AddressMapPicker } from '../../components/address/AddressMapPicker';
import type { GeocodeResult, GeoPoint } from '../../utils/geocoding';
import '../../styles/components/admin-site-settings.css';

/** Một ô trong form — khai báo theo dữ liệu để thêm giá trị mới chỉ tốn 1 dòng. */
/** Các khoá do khối riêng quản lý (ảnh banner, vị trí cửa hàng) — không render bằng ô nhập chung */
type CustomKey = 'heroImageUrl' | 'contactAddress' | 'storeLatitude' | 'storeLongitude' | 'deliveryMaxRadiusKm';

interface FieldSpec {
  key: Exclude<keyof SiteSettings, CustomKey>;
  label: string;
  hint?: string;
  placeholder?: string;
  maxLength: number;
  /** Có thì render ô nhiều dòng và cho chiếm cả hàng */
  textarea?: boolean;
}

interface SectionSpec {
  id: string;
  title: string;
  description: string;
  fields: FieldSpec[];
}

/**
 * Form chia nhóm theo vị trí hiển thị trên website, không theo thứ tự key trong DB.
 * `heroImageUrl` không nằm ở đây vì nó do khối chọn ảnh quản lý riêng.
 */
const SECTIONS: SectionSpec[] = [
  {
    id: 'identity',
    title: 'Nhận diện thương hiệu',
    description: 'Tên và dòng giới thiệu ngắn — hiện ở header, footer, trang đăng nhập và sidebar quản trị.',
    fields: [
      { key: 'siteName', label: 'Tên website', maxLength: 120, placeholder: 'Bánh Mỳ King' },
      { key: 'tagline', label: 'Dòng phụ dưới tên', maxLength: 160, placeholder: 'Vỏ giòn · nhân đầy' },
    ],
  },
  {
    id: 'contact',
    title: 'Thông tin liên hệ',
    description:
      'Hiện ở cột "Liên hệ" ngoài footer. Để trống thì cột này tự ẩn. Địa chỉ cửa hàng nằm ở mục "Vị trí cửa hàng & giao hàng".',
    fields: [
      { key: 'contactPhone', label: 'Số điện thoại', maxLength: 30, placeholder: '0901 234 567' },
      { key: 'contactEmail', label: 'Email liên hệ', maxLength: 255, placeholder: 'lienhe@banhmyking.vn' },
    ],
  },
  {
    id: 'announcement',
    title: 'Thanh thông báo',
    description: 'Dải chữ chạy trên cùng của trang khách hàng.',
    fields: [
      { key: 'announcementPrimary', label: 'Dòng bên trái', maxLength: 200, textarea: true },
      { key: 'announcementSecondary', label: 'Dòng bên phải', maxLength: 200, textarea: true },
    ],
  },
  {
    id: 'hero',
    title: 'Banner trang chủ',
    description: 'Khối lớn đầu trang thực đơn. Tiêu đề tách làm hai dòng, dòng 2 có phần tô màu nhấn.',
    fields: [
      { key: 'heroBadge', label: 'Nhãn nhỏ phía trên tiêu đề', maxLength: 160, textarea: true },
      { key: 'heroTitle', label: 'Tiêu đề — dòng 1', maxLength: 160 },
      { key: 'heroTitleLead', label: 'Tiêu đề — đầu dòng 2', maxLength: 160 },
      { key: 'heroTitleHighlight', label: 'Tiêu đề — cuối dòng 2 (tô màu)', maxLength: 80 },
      { key: 'heroDescription', label: 'Mô tả', maxLength: 400, textarea: true },
    ],
  },
  {
    id: 'footer',
    title: 'Footer',
    description: 'Đoạn giới thiệu ngắn ở chân trang.',
    fields: [
      { key: 'footerDescription', label: 'Mô tả ở footer', maxLength: 500, textarea: true },
    ],
  },
];

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
/** SĐT Việt Nam: số, khoảng trắng, dấu chấm, gạch ngang, dấu + ở đầu */
const PHONE_PATTERN = /^\+?[\d\s.-]{8,30}$/;

/** Bắt buộc: tên web (hiện ở header/footer/trang đăng nhập) và tiêu đề hero (thẻ h1). */
const REQUIRED_KEYS: (keyof SiteSettings)[] = ['siteName', 'heroTitle'];

const validate = (draft: SiteSettings): Partial<Record<keyof SiteSettings, string>> => {
  const errors: Partial<Record<keyof SiteSettings, string>> = {};

  if (!draft.siteName.trim()) {
    errors.siteName = 'Tên website không được để trống.';
  }
  if (!draft.heroTitle.trim()) {
    errors.heroTitle = 'Tiêu đề dòng 1 không được để trống.';
  }
  if (draft.contactEmail.trim() && !EMAIL_PATTERN.test(draft.contactEmail.trim())) {
    errors.contactEmail = 'Email không đúng định dạng.';
  }
  if (draft.contactPhone.trim() && !PHONE_PATTERN.test(draft.contactPhone.trim())) {
    errors.contactPhone = 'Số điện thoại chỉ gồm số, khoảng trắng, dấu . - và dấu + ở đầu.';
  }
  const radius = draft.deliveryMaxRadiusKm.trim();
  if (radius && !(Number(radius) >= 0.5 && Number(radius) <= 100)) {
    errors.deliveryMaxRadiusKm = 'Bán kính giao hàng phải là số từ 0.5 đến 100 (km), hoặc để trống = không giới hạn.';
  }

  return errors;
};

interface SettingsFormProps {
  initial: SiteSettings;
}

const SettingsForm = ({ initial }: SettingsFormProps) => {
  const { refresh } = useSiteSettings();
  const toast = useToast();
  const confirm = useConfirm();

  const [draft, setDraft] = useState<SiteSettings>(initial);
  const [errors, setErrors] = useState<Partial<Record<keyof SiteSettings, string>>>({});
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [isUploadingImage, setIsUploadingImage] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const setField = (key: keyof SiteSettings, value: string) => {
    setDraft((prev) => ({ ...prev, [key]: value }));
    setErrors((prev) => ({ ...prev, [key]: undefined }));
  };

  // Vị trí cửa hàng: lưu chuỗi 6 chữ số thập phân (~0,1 m) — đủ chính xác, gọn trong DB
  const storePoint: GeoPoint | null =
    draft.storeLatitude && draft.storeLongitude
      ? { latitude: Number(draft.storeLatitude), longitude: Number(draft.storeLongitude) }
      : null;

  const handleStorePick = (point: GeoPoint, address: GeocodeResult | null) => {
    setDraft((prev) => ({
      ...prev,
      storeLatitude: point.latitude.toFixed(6),
      storeLongitude: point.longitude.toFixed(6),
      // Ghim mới thì gợi ý địa chỉ theo bản đồ — admin sửa lại ở ô bên dưới nếu chưa đúng
      ...(address ? { contactAddress: address.fullAddress } : {}),
    }));
  };

  const handleSave = async () => {
    const found = validate(draft);
    if (Object.keys(found).length > 0) {
      setErrors(found);
      setErrorMsg('Vui lòng kiểm tra lại các ô được đánh dấu đỏ.');
      return;
    }

    setIsSaving(true);
    setErrorMsg(null);
    try {
      await siteSettingsApi.update(draft);
      // Đẩy giá trị mới vào context để header/footer/hero đang hiện cập nhật ngay
      await refresh();
      toast.success('Đã lưu cấu hình trang web');
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : 'Lưu cấu hình thất bại');
    } finally {
      setIsSaving(false);
    }
  };

  const handleImagePick = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    // Reset input để chọn lại cùng một file vẫn kích hoạt onChange
    event.target.value = '';
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      toast.error('Vui lòng chọn tệp hình ảnh (JPG, PNG, WEBP, GIF)');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      toast.error('Dung lượng ảnh không được vượt quá 5MB');
      return;
    }

    setIsUploadingImage(true);
    try {
      const url = await siteSettingsApi.uploadHeroImage(file);
      setDraft((prev) => ({ ...prev, heroImageUrl: url }));
      // Ảnh được ghi vào DB ngay ở bước này (khác các ô chữ, chỉ ghi khi bấm Lưu)
      await refresh();
      toast.success('Đã tải ảnh banner lên');
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Tải ảnh banner thất bại');
    } finally {
      setIsUploadingImage(false);
    }
  };

  const handleRemoveImage = async () => {
    const accepted = await confirm({
      title: 'Xoá ảnh banner?',
      message: 'Ảnh sẽ bị xoá khỏi máy chủ và banner quay về mặc định. Không hoàn tác được.',
      confirmText: 'Xoá ảnh',
      danger: true,
    });
    if (!accepted) return;

    setIsUploadingImage(true);
    try {
      await siteSettingsApi.update({ heroImageUrl: '' });
      setDraft((prev) => ({ ...prev, heroImageUrl: '' }));
      await refresh();
      toast.success('Đã xoá ảnh banner');
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Xoá ảnh banner thất bại');
    } finally {
      setIsUploadingImage(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Cấu hình trang web"
        subtitle="Sửa nội dung hiển thị của website. Thay đổi áp dụng cho mọi khách truy cập."
        actions={
          <Button icon={<Save size={17} />} loading={isSaving} onClick={handleSave}>
            Lưu thay đổi
          </Button>
        }
      />

      {errorMsg && (
        <div className="alert-banner alert-error page-alert" role="alert">
          <XCircle size={18} />
          <div>{errorMsg}</div>
        </div>
      )}

      {SECTIONS.map((section) => (
        <section className="card" key={section.id}>
          <div className="card__head">
            <div>
              <h2 className="card__title">{section.title}</h2>
              <p className="asettings__section-desc">{section.description}</p>
            </div>
          </div>

          <div className="card__body">
            {/* Banner là khối riêng vì là ảnh tải lên, không phải ô nhập chữ */}
            {section.id === 'hero' && (
              <div className="asettings__banner">
                <div className="asettings__banner-preview">
                  {draft.heroImageUrl ? (
                    <img src={draft.heroImageUrl} alt="Xem trước ảnh banner" />
                  ) : (
                    <span className="asettings__banner-empty">
                      <ImageIcon size={26} />
                      Đang dùng icon mặc định
                    </span>
                  )}
                </div>

                <div className="asettings__banner-actions">
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/*"
                    hidden
                    onChange={handleImagePick}
                  />
                  <Button
                    variant="secondary"
                    icon={<Upload size={16} />}
                    loading={isUploadingImage}
                    onClick={() => fileInputRef.current?.click()}
                  >
                    {draft.heroImageUrl ? 'Đổi ảnh' : 'Tải ảnh lên'}
                  </Button>
                  {draft.heroImageUrl && (
                    <Button variant="ghost" icon={<Trash2 size={16} />} onClick={handleRemoveImage}>
                      Xoá ảnh
                    </Button>
                  )}
                  <p className="asettings__banner-hint">
                    Ảnh tối đa 5MB (JPG, PNG, WEBP, GIF). Ảnh áp dụng ngay khi tải lên; các ô chữ bên dưới áp
                    dụng sau khi bấm <strong>Lưu thay đổi</strong>.
                  </p>
                </div>
              </div>
            )}

            <div className="asettings__grid">
              {section.fields.map((field) => {
                const common = {
                  label: field.label,
                  error: errors[field.key],
                  placeholder: field.placeholder,
                  maxLength: field.maxLength,
                  value: draft[field.key],
                  onChange: (
                    event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
                  ) => setField(field.key, event.target.value),
                  required: REQUIRED_KEYS.includes(field.key),
                };

                return (
                  <div
                    key={field.key}
                    className={field.textarea ? 'asettings__field asettings__field--wide' : 'asettings__field'}
                  >
                    {field.textarea ? <Textarea {...common} rows={2} /> : <Input {...common} />}
                  </div>
                );
              })}
            </div>
          </div>
        </section>
      ))}

      <section className="card">
        <div className="card__head">
          <div>
            <h2 className="card__title">Vị trí cửa hàng &amp; giao hàng</h2>
            <p className="asettings__section-desc">
              Ghim đúng vị trí quán trên bản đồ — hệ thống dùng điểm này để tự tính khoảng cách và phí giao hàng
              cho từng đơn. Chưa ghim thì phí ship tính theo khu vực (nội/ngoại thành). Địa chỉ hiện ở cột
              "Liên hệ" ngoài footer.
            </p>
          </div>
        </div>
        <div className="card__body asettings__store">
          <AddressMapPicker value={storePoint} onPick={handleStorePick} height={320} />
          {storePoint && (
            <div className="asettings__store-pin">
              <span>
                Đã ghim: {draft.storeLatitude}, {draft.storeLongitude}
              </span>
              <Button
                variant="ghost"
                size="sm"
                icon={<MapPinOff size={15} />}
                onClick={() => setDraft((prev) => ({ ...prev, storeLatitude: '', storeLongitude: '' }))}
              >
                Bỏ ghim
              </Button>
            </div>
          )}
          <div className="asettings__grid">
            <div className="asettings__field asettings__field--wide">
              <Textarea
                label="Địa chỉ cửa hàng"
                rows={2}
                maxLength={255}
                value={draft.contactAddress}
                onChange={(event) => setField('contactAddress', event.target.value)}
                hint="Tự điền khi ghim trên bản đồ — sửa lại nếu chưa đúng. Để trống thì footer không hiện địa chỉ."
                placeholder="12 Láng Hạ, Phường Giảng Võ, Hà Nội"
              />
            </div>
            <div className="asettings__field">
              <Input
                label="Bán kính giao hàng tối đa (km)"
                type="number"
                min={0.5}
                max={100}
                step={0.5}
                value={draft.deliveryMaxRadiusKm}
                onChange={(event) => setField('deliveryMaxRadiusKm', event.target.value)}
                error={errors.deliveryMaxRadiusKm}
                hint="Tính theo quãng đường ước tính. Đơn xa hơn sẽ bị từ chối. Để trống = không giới hạn."
              />
            </div>
          </div>
        </div>
      </section>

      <div className="asettings__foot">
        {/*
          Hoàn tác chỉ trả lại các ô chữ. Ảnh banner đã ghi xuống máy chủ ngay lúc tải lên,
          trả heroImageUrl về giá trị cũ sẽ khiến lần Lưu sau xoá mất file vừa tải.
        */}
        <Button
          icon={<RotateCcw size={16} />}
          variant="secondary"
          onClick={() => setDraft((prev) => ({ ...initial, heroImageUrl: prev.heroImageUrl }))}
        >
          Hoàn tác
        </Button>
        <Button icon={<Save size={17} />} loading={isSaving} onClick={handleSave}>
          Lưu thay đổi
        </Button>
      </div>
    </>
  );
};

export const AdminSiteSettingsPage = () => {
  const { settings, isLoading } = useSiteSettings();

  return (
    <div className="asettings">
      {isLoading ? (
        <>
          <PageHeader title="Cấu hình trang web" subtitle="Đang tải cấu hình..." />
          <section className="card">
            <div className="card__body">
              <Skeleton variant="row" count={8} />
            </div>
          </section>
        </>
      ) : (
        // Form chỉ mount sau khi tải xong để state khởi tạo bằng đúng giá trị đã lưu
        <SettingsForm initial={settings} />
      )}
    </div>
  );
};
