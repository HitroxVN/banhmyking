import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { MapPin, Pencil, Plus, SearchX, Star, Trash2 } from 'lucide-react';
import { addressApi } from '../api/addressApi';
import { authApi } from '../api/authApi';
import { useAuth } from '../context/useAuth';
import {
  Badge,
  Button,
  EmptyState,
  Input,
  Modal,
  Skeleton,
  Tabs,
  Textarea,
  useConfirm,
  useToast,
} from '../components/ui';
import type { AddressRequest, AddressResponse } from '../types/address';
import '../styles/components/order.css';
import '../styles/components/profile.css';

type TabKey = 'info' | 'password' | 'address';

/** SĐT Việt Nam: 10 số, đầu 03/05/07/08/09 */
const PHONE_REGEX = /^(0[35789])[0-9]{8}$/;

interface AddressForm extends AddressRequest {
  id: number | null;
}

export const ProfilePage = () => {
  const { user, refreshUserProfile, logout } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const confirm = useConfirm();

  const [tab, setTab] = useState<TabKey>('info');

  // ── Tab thông tin ──────────────────────────────────────────────────────────
  const [fullName, setFullName] = useState(user?.fullName ?? '');
  const [phone, setPhone] = useState(user?.phone ?? '');
  const [infoErrors, setInfoErrors] = useState<{ fullName?: string; phone?: string }>({});
  const [isSavingInfo, setIsSavingInfo] = useState(false);

  // ── Tab mật khẩu ───────────────────────────────────────────────────────────
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [pwdErrors, setPwdErrors] = useState<{
    oldPassword?: string;
    newPassword?: string;
    confirmPassword?: string;
  }>({});
  const [isSavingPwd, setIsSavingPwd] = useState(false);

  // ── Tab sổ địa chỉ ─────────────────────────────────────────────────────────
  const [addresses, setAddresses] = useState<AddressResponse[]>([]);
  const [addressesLoaded, setAddressesLoaded] = useState(false);
  const [isLoadingAddresses, setIsLoadingAddresses] = useState(false);
  const [addressError, setAddressError] = useState<string | null>(null);
  const [addressForm, setAddressForm] = useState<AddressForm | null>(null);
  const [formErrors, setFormErrors] = useState<{ receiverName?: string; receiverPhone?: string; fullAddress?: string }>({});
  const [isSavingAddress, setIsSavingAddress] = useState(false);

  const loadAddresses = useCallback(async () => {
    setIsLoadingAddresses(true);
    setAddressError(null);
    try {
      setAddresses(await addressApi.getAddresses());
    } catch {
      setAddressError('Không tải được sổ địa chỉ. Vui lòng thử lại.');
    } finally {
      setIsLoadingAddresses(false);
    }
  }, []);

  // Nạp sổ địa chỉ đúng một lần, khi khách mở tab
  useEffect(() => {
    if (tab !== 'address' || addressesLoaded) return;
    setAddressesLoaded(true);
    loadAddresses();
  }, [tab, addressesLoaded, loadAddresses]);

  const handleSaveInfo = async () => {
    const errors: typeof infoErrors = {};
    if (!fullName.trim()) errors.fullName = 'Vui lòng nhập họ và tên';
    else if (fullName.trim().length < 2) errors.fullName = 'Họ và tên phải có ít nhất 2 ký tự';

    const trimmedPhone = phone.trim().replace(/\s/g, '');
    if (trimmedPhone && !PHONE_REGEX.test(trimmedPhone)) {
      errors.phone = 'Số điện thoại không đúng định dạng Việt Nam (10 chữ số)';
    }

    setInfoErrors(errors);
    if (Object.values(errors).some(Boolean)) return;

    setIsSavingInfo(true);
    try {
      await authApi.updateProfile({ fullName: fullName.trim(), phone: trimmedPhone || undefined });
      await refreshUserProfile();
      toast.success('Đã cập nhật hồ sơ');
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Cập nhật hồ sơ thất bại');
    } finally {
      setIsSavingInfo(false);
    }
  };

  const handleChangePassword = async () => {
    const errors: typeof pwdErrors = {};
    if (!oldPassword) errors.oldPassword = 'Vui lòng nhập mật khẩu hiện tại';
    if (!newPassword) errors.newPassword = 'Vui lòng nhập mật khẩu mới';
    else if (newPassword.length < 8) errors.newPassword = 'Mật khẩu phải chứa ít nhất 8 ký tự';
    else if (newPassword === oldPassword) errors.newPassword = 'Mật khẩu mới phải khác mật khẩu hiện tại';
    if (confirmPassword !== newPassword) errors.confirmPassword = 'Mật khẩu xác nhận không trùng khớp';

    setPwdErrors(errors);
    if (Object.values(errors).some(Boolean)) return;

    setIsSavingPwd(true);
    try {
      await authApi.changePassword({ oldPassword, newPassword });
      toast.success('Đổi mật khẩu thành công — vui lòng đăng nhập lại');
      await logout();
      navigate('/login', { replace: true });
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Đổi mật khẩu thất bại');
    } finally {
      setIsSavingPwd(false);
    }
  };

  const openAddressForm = (address?: AddressResponse) => {
    setFormErrors({});
    setAddressForm({
      id: address?.id ?? null,
      receiverName: address?.receiverName ?? '',
      receiverPhone: address?.receiverPhone ?? '',
      fullAddress: address?.fullAddress ?? '',
      defaultAddress: address?.defaultAddress ?? false,
    });
  };

  const handleSaveAddress = async () => {
    if (!addressForm) return;

    const errors: typeof formErrors = {};
    if (!addressForm.receiverName.trim()) errors.receiverName = 'Vui lòng nhập tên người nhận';
    const trimmedPhone = addressForm.receiverPhone.trim().replace(/\s/g, '');
    if (!trimmedPhone) errors.receiverPhone = 'Vui lòng nhập số điện thoại';
    else if (!PHONE_REGEX.test(trimmedPhone)) errors.receiverPhone = 'Số điện thoại không đúng định dạng Việt Nam';
    if (addressForm.fullAddress.trim().length < 5) errors.fullAddress = 'Địa chỉ quá ngắn, vui lòng ghi rõ số nhà và đường';

    setFormErrors(errors);
    if (Object.values(errors).some(Boolean)) return;

    const payload: AddressRequest = {
      receiverName: addressForm.receiverName.trim(),
      receiverPhone: trimmedPhone,
      fullAddress: addressForm.fullAddress.trim(),
      defaultAddress: addressForm.defaultAddress,
    };

    setIsSavingAddress(true);
    try {
      if (addressForm.id) {
        await addressApi.updateAddress(addressForm.id, payload);
        toast.success('Đã cập nhật địa chỉ');
      } else {
        await addressApi.createAddress(payload);
        toast.success('Đã thêm địa chỉ mới');
      }
      setAddressForm(null);
      await loadAddresses();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Lưu địa chỉ thất bại');
    } finally {
      setIsSavingAddress(false);
    }
  };

  const handleSetDefault = async (address: AddressResponse) => {
    try {
      await addressApi.setDefaultAddress(address.id);
      toast.success('Đã đặt làm địa chỉ mặc định');
      await loadAddresses();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Thao tác thất bại');
    }
  };

  const handleDeleteAddress = async (address: AddressResponse) => {
    const accepted = await confirm({
      title: 'Xoá địa chỉ',
      message: `Xoá địa chỉ của ${address.receiverName}? Thao tác này không thể hoàn tác.`,
      confirmText: 'Xoá',
      danger: true,
    });
    if (!accepted) return;

    try {
      await addressApi.deleteAddress(address.id);
      toast.success('Đã xoá địa chỉ');
      await loadAddresses();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Xoá địa chỉ thất bại');
    }
  };

  return (
    <div>
      <div className="page-bar">
        <div>
          <p className="page-bar__crumb">Trang chủ / Hồ sơ</p>
          <h1 className="page-bar__title">Hồ sơ cá nhân</h1>
        </div>
      </div>

      <Tabs<TabKey>
        value={tab}
        onChange={setTab}
        tabs={[
          { key: 'info', label: 'Thông tin' },
          { key: 'password', label: 'Đổi mật khẩu' },
          { key: 'address', label: 'Sổ địa chỉ' },
        ]}
      />

      {tab === 'info' && (
        <section className="card">
          <div className="card__head">
            <h2 className="card__title">Thông tin tài khoản</h2>
            <Badge tone="info">{user?.role ?? 'CUSTOMER'}</Badge>
          </div>
          <div className="card__body">
            <div className="profile__form">
              <Input label="Email" value={user?.email ?? ''} readOnly disabled hint="Email dùng để đăng nhập, không thể thay đổi." />
              <Input
                label="Họ và tên"
                required
                value={fullName}
                onChange={(event) => {
                  setFullName(event.target.value);
                  if (infoErrors.fullName) setInfoErrors({ ...infoErrors, fullName: undefined });
                }}
                error={infoErrors.fullName}
                placeholder="Nguyễn Văn A"
              />
              <Input
                label="Số điện thoại"
                type="tel"
                value={phone}
                onChange={(event) => {
                  setPhone(event.target.value);
                  if (infoErrors.phone) setInfoErrors({ ...infoErrors, phone: undefined });
                }}
                error={infoErrors.phone}
                placeholder="0912 345 678"
                hint="Dùng để tài xế liên hệ khi giao hàng."
              />
              <div>
                <Button loading={isSavingInfo} onClick={handleSaveInfo}>
                  Lưu thay đổi
                </Button>
              </div>
            </div>
          </div>
        </section>
      )}

      {tab === 'password' && (
        <section className="card">
          <div className="card__head">
            <h2 className="card__title">Đổi mật khẩu</h2>
          </div>
          <div className="card__body">
            <div className="profile__form">
              <Input
                label="Mật khẩu hiện tại"
                type="password"
                required
                value={oldPassword}
                onChange={(event) => {
                  setOldPassword(event.target.value);
                  if (pwdErrors.oldPassword) setPwdErrors({ ...pwdErrors, oldPassword: undefined });
                }}
                error={pwdErrors.oldPassword}
                autoComplete="current-password"
              />
              <Input
                label="Mật khẩu mới"
                type="password"
                required
                value={newPassword}
                onChange={(event) => {
                  setNewPassword(event.target.value);
                  if (pwdErrors.newPassword) setPwdErrors({ ...pwdErrors, newPassword: undefined });
                }}
                error={pwdErrors.newPassword}
                hint="Tối thiểu 8 ký tự."
                autoComplete="new-password"
              />
              <Input
                label="Nhập lại mật khẩu mới"
                type="password"
                required
                value={confirmPassword}
                onChange={(event) => {
                  setConfirmPassword(event.target.value);
                  if (pwdErrors.confirmPassword) setPwdErrors({ ...pwdErrors, confirmPassword: undefined });
                }}
                error={pwdErrors.confirmPassword}
                autoComplete="new-password"
              />
              <div>
                <Button loading={isSavingPwd} onClick={handleChangePassword}>
                  Đổi mật khẩu
                </Button>
              </div>
            </div>
            <p className="profile__note">
              Vì lý do an toàn, mọi phiên đăng nhập trên các thiết bị khác sẽ bị thu hồi sau khi đổi mật khẩu.
            </p>
          </div>
        </section>
      )}

      {tab === 'address' && (
        <section className="card">
          <div className="card__head">
            <h2 className="card__title">Sổ địa chỉ giao hàng</h2>
            <Button size="sm" icon={<Plus size={16} />} onClick={() => openAddressForm()}>
              Thêm địa chỉ
            </Button>
          </div>
          <div className="card__body">
            {isLoadingAddresses && <Skeleton variant="row" count={2} />}

            {!isLoadingAddresses && addressError && (
              <EmptyState
                icon={<SearchX size={30} />}
                title="Chưa tải được sổ địa chỉ"
                description={addressError}
                action={<Button onClick={loadAddresses}>Thử lại</Button>}
              />
            )}

            {!isLoadingAddresses && !addressError && addresses.length === 0 && (
              <EmptyState
                icon={<MapPin size={30} />}
                title="Chưa có địa chỉ nào"
                description="Lưu sẵn địa chỉ nhận hàng để đặt món nhanh hơn ở bước thanh toán."
                action={<Button onClick={() => openAddressForm()}>Thêm địa chỉ đầu tiên</Button>}
              />
            )}

            {!isLoadingAddresses && !addressError && addresses.length > 0 && (
              <div className="profile__list">
                {addresses.map((address) => (
                  <article
                    key={address.id}
                    className={`addr-card${address.defaultAddress ? ' addr-card--default' : ''}`}
                  >
                    <div>
                      <p className="addr-card__name">
                        {address.receiverName} · {address.receiverPhone}
                        {address.defaultAddress && (
                          <Badge tone="info" icon={<Star size={12} />}>
                            Mặc định
                          </Badge>
                        )}
                      </p>
                      <p className="addr-card__text">{address.fullAddress}</p>
                    </div>

                    <div className="addr-card__actions">
                      {!address.defaultAddress && (
                        <Button size="sm" variant="ghost" onClick={() => handleSetDefault(address)}>
                          Đặt mặc định
                        </Button>
                      )}
                      <Button
                        size="sm"
                        variant="secondary"
                        icon={<Pencil size={15} />}
                        onClick={() => openAddressForm(address)}
                      >
                        Sửa
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        icon={<Trash2 size={15} />}
                        onClick={() => handleDeleteAddress(address)}
                      >
                        Xoá
                      </Button>
                    </div>
                  </article>
                ))}
              </div>
            )}
          </div>
        </section>
      )}

      <Modal
        open={addressForm !== null}
        onClose={() => setAddressForm(null)}
        title={addressForm?.id ? 'Sửa địa chỉ' : 'Thêm địa chỉ mới'}
        footer={
          <>
            <Button variant="secondary" onClick={() => setAddressForm(null)}>
              Huỷ
            </Button>
            <Button loading={isSavingAddress} onClick={handleSaveAddress}>
              Lưu địa chỉ
            </Button>
          </>
        }
      >
        {addressForm && (
          <div className="profile__form">
            <Input
              label="Tên người nhận"
              required
              value={addressForm.receiverName}
              onChange={(event) => setAddressForm({ ...addressForm, receiverName: event.target.value })}
              error={formErrors.receiverName}
              placeholder="Nguyễn Văn A"
            />
            <Input
              label="Số điện thoại"
              type="tel"
              required
              value={addressForm.receiverPhone}
              onChange={(event) => setAddressForm({ ...addressForm, receiverPhone: event.target.value })}
              error={formErrors.receiverPhone}
              placeholder="0912 345 678"
            />
            <Textarea
              label="Địa chỉ đầy đủ"
              required
              rows={3}
              value={addressForm.fullAddress}
              onChange={(event) => setAddressForm({ ...addressForm, fullAddress: event.target.value })}
              error={formErrors.fullAddress}
              placeholder="Số nhà, đường, phường/xã, quận/huyện, tỉnh/thành"
            />
            <label className="ui-check">
              <input
                type="checkbox"
                checked={addressForm.defaultAddress}
                onChange={(event) => setAddressForm({ ...addressForm, defaultAddress: event.target.checked })}
              />
              Đặt làm địa chỉ mặc định
            </label>
          </div>
        )}
      </Modal>
    </div>
  );
};
