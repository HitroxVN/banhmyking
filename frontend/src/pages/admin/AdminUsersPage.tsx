import { useCallback, useEffect, useState } from 'react';
import { Lock, Pencil, Plus, Search, ShieldCheck, Unlock, UserPlus, XCircle } from 'lucide-react';
import {
  Badge,
  Button,
  EmptyState,
  Input,
  Modal,
  PageHeader,
  Pagination,
  Select,
  Skeleton,
  useConfirm,
  useToast,
} from '../../components/ui';
import type { BadgeTone } from '../../components/ui';
import { adminUserApi } from '../../api/adminUserApi';
import type { AdminCreateUserPayload, AdminUpdateUserPayload, AdminUser } from '../../types/admin';
import type { RoleName } from '../../types/auth';
import { useAuth } from '../../context/useAuth';
import '../../styles/components/admin-users.css';

const PAGE_SIZE = 10;

const ROLE_LABEL: Record<RoleName, string> = {
  CUSTOMER: 'Khách hàng',
  STAFF: 'Nhân viên bếp',
  SHIPPER: 'Tài xế giao hàng',
  ADMIN: 'Quản trị viên',
};
const ROLE_TONE: Record<RoleName, BadgeTone> = {
  CUSTOMER: 'neutral',
  STAFF: 'info',
  SHIPPER: 'warning',
  ADMIN: 'success',
};
const ROLE_OPTIONS = (Object.keys(ROLE_LABEL) as RoleName[]).map((role) => ({
  value: role,
  label: ROLE_LABEL[role],
}));
/** Tài khoản nội bộ được tạo trực tiếp; ADMIN chỉ cấp qua chức năng đổi vai trò. */
const CREATABLE_ROLES = ROLE_OPTIONS.filter((option) => option.value !== 'ADMIN');

const initialCreateForm: AdminCreateUserPayload = {
  email: '',
  password: '',
  fullName: '',
  phone: '',
  role: 'STAFF',
};

export const AdminUsersPage = () => {
  const { user: currentUser } = useAuth();

  const [users, setUsers] = useState<AdminUser[]>([]);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);

  const [searchInput, setSearchInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [roleFilter, setRoleFilter] = useState<RoleName | 'ALL'>('ALL');
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'ACTIVE' | 'BANNED'>('ALL');

  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  const [createOpen, setCreateOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<AdminUser | null>(null);
  const [roleTargetUser, setRoleTargetUser] = useState<AdminUser | null>(null);

  const confirm = useConfirm();
  const toast = useToast();

  const load = useCallback(
    async (manual = false) => {
      if (manual) setIsRefreshing(true);
      setErrorMsg(null);
      try {
        const data = await adminUserApi.getUsers({
          page: page - 1,
          size: PAGE_SIZE,
          keyword: keyword || undefined,
          role: roleFilter === 'ALL' ? undefined : roleFilter,
          banned: statusFilter === 'ALL' ? undefined : statusFilter === 'BANNED',
        });
        setUsers(data.content ?? []);
        setTotalPages(data.totalPages || 1);
        setTotalElements(data.totalElements ?? 0);
      } catch (err: unknown) {
        setErrorMsg(err instanceof Error ? err.message : 'Không tải được danh sách tài khoản.');
      } finally {
        setIsLoading(false);
        setIsRefreshing(false);
      }
    },
    [page, keyword, roleFilter, statusFilter]
  );

  useEffect(() => {
    setIsLoading(true);
    void load();
  }, [load]);

  const reload = () => {
    setIsLoading(true);
    void load(true);
  };

  const handleSearch = (event: React.FormEvent) => {
    event.preventDefault();
    setPage(1);
    setKeyword(searchInput.trim());
  };

  const handleToggleStatus = async (target: AdminUser) => {
    if (currentUser?.id === target.id) {
      toast.error('Bạn không thể tự khoá tài khoản của chính mình.');
      return;
    }

    const isLocking = !target.banned;
    const ok = await confirm({
      title: isLocking ? 'Khoá tài khoản' : 'Mở khoá tài khoản',
      message: isLocking
        ? `Khoá tài khoản "${target.fullName}"? Toàn bộ phiên đăng nhập của người này sẽ bị chấm dứt ngay lập tức.`
        : `Mở khoá tài khoản "${target.fullName}"? Người dùng sẽ đăng nhập được bình thường.`,
      confirmText: isLocking ? 'Khoá tài khoản' : 'Mở khoá',
      danger: isLocking,
    });
    if (!ok) return;

    setBusyId(target.id);
    try {
      await adminUserApi.changeStatus(target.id, isLocking);
      toast.success(`Đã ${isLocking ? 'khoá' : 'mở khoá'} tài khoản ${target.fullName}`);
      await load();
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Cập nhật trạng thái thất bại.');
    } finally {
      setBusyId(null);
    }
  };

  const handleDelete = async (target: AdminUser) => {
    if (currentUser?.id === target.id) {
      toast.error('Bạn không thể tự xoá tài khoản của chính mình.');
      return;
    }

    const ok = await confirm({
      title: 'Xoá tài khoản',
      message: `Xoá tài khoản "${target.fullName}" (${target.email})? Tài khoản bị xoá mềm và toàn bộ token đăng nhập bị thu hồi.`,
      confirmText: 'Xoá tài khoản',
      danger: true,
    });
    if (!ok) return;

    setBusyId(target.id);
    try {
      await adminUserApi.deleteUser(target.id);
      toast.success(`Đã xoá tài khoản ${target.fullName}`);
      await load();
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Xoá tài khoản thất bại.');
    } finally {
      setBusyId(null);
    }
  };

  return (
    <>
      <PageHeader
        title="Tài khoản & phân quyền"
        subtitle={`${totalElements} tài khoản trong hệ thống`}
        onRefresh={reload}
        isRefreshing={isRefreshing}
      />

      {errorMsg && (
        <div className="alert-banner alert-error page-alert" role="alert">
          <XCircle size={18} />
          <div>{errorMsg}</div>
        </div>
      )}

      <section className="card">
        <div className="ausers__filters">
          <form className="ausers__search" onSubmit={handleSearch} role="search">
            <Input
              label="Tìm kiếm"
              type="search"
              placeholder="Tên hoặc email người dùng..."
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
            />
            <Button type="submit" variant="secondary" icon={<Search size={16} />}>
              Tìm
            </Button>
          </form>

          <Select
            label="Vai trò"
            value={roleFilter}
            onChange={(event) => {
              setRoleFilter(event.target.value as RoleName | 'ALL');
              setPage(1);
            }}
          >
            <option value="ALL">Tất cả vai trò</option>
            {ROLE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </Select>

          <Select
            label="Trạng thái"
            value={statusFilter}
            onChange={(event) => {
              setStatusFilter(event.target.value as 'ALL' | 'ACTIVE' | 'BANNED');
              setPage(1);
            }}
          >
            <option value="ALL">Tất cả trạng thái</option>
            <option value="ACTIVE">Đang hoạt động</option>
            <option value="BANNED">Đã bị khoá</option>
          </Select>

          <Button variant="primary" icon={<Plus size={17} />} onClick={() => setCreateOpen(true)}>
            Thêm tài khoản
          </Button>
        </div>
      </section>

      <section className="card">
        {isLoading ? (
          <div className="card__body">
            <Skeleton variant="row" count={6} />
          </div>
        ) : users.length === 0 ? (
          <div className="card__body">
            <EmptyState
              icon={<UserPlus size={30} />}
              title="Không tìm thấy tài khoản nào"
              description="Thử đổi từ khoá tìm kiếm hoặc bộ lọc vai trò / trạng thái."
            />
          </div>
        ) : (
          <div className="table-wrap">
            <table className="ui-table">
              <thead>
                <tr>
                  <th>Người dùng</th>
                  <th>Điện thoại</th>
                  <th>Vai trò</th>
                  <th>Trạng thái</th>
                  <th>Ngày tạo</th>
                  <th aria-label="Hành động" />
                </tr>
              </thead>
              <tbody>
                {users.map((item) => {
                  const isSelf = currentUser?.id === item.id;
                  const busy = busyId === item.id;

                  return (
                    <tr key={item.id}>
                      <td>
                        <div className="ausers__user">
                          <span className="ausers__avatar" aria-hidden="true">
                            {item.fullName.charAt(0).toUpperCase() || 'U'}
                          </span>
                          <span>
                            <span className="ui-table__primary">
                              {item.fullName}
                              {isSelf && <span className="ausers__you">Bạn</span>}
                            </span>
                            <span className="ui-table__meta">{item.email}</span>
                          </span>
                        </div>
                      </td>
                      <td>
                        {item.phone || <span className="ui-table__meta">Chưa cập nhật</span>}
                      </td>
                      <td>
                        <Badge tone={ROLE_TONE[item.role] ?? 'neutral'}>
                          {ROLE_LABEL[item.role] ?? item.role}
                        </Badge>
                      </td>
                      <td>
                        <Badge tone={item.banned ? 'danger' : 'success'}>
                          {item.banned ? 'Đã bị khoá' : 'Hoạt động'}
                        </Badge>
                      </td>
                      <td>
                        {item.createdAt ? (
                          new Date(item.createdAt).toLocaleDateString('vi-VN')
                        ) : (
                          <span className="ui-table__meta">—</span>
                        )}
                      </td>
                      <td>
                        <div className="ui-table__actions">
                          <Button
                            size="sm"
                            variant="secondary"
                            icon={<Pencil size={15} />}
                            onClick={() => setEditingUser(item)}
                          >
                            Sửa
                          </Button>
                          {!isSelf && (
                            <Button
                              size="sm"
                              variant="secondary"
                              icon={<ShieldCheck size={15} />}
                              onClick={() => setRoleTargetUser(item)}
                            >
                              Vai trò
                            </Button>
                          )}
                          <Button
                            size="sm"
                            variant="ghost"
                            icon={item.banned ? <Unlock size={15} /> : <Lock size={15} />}
                            disabled={isSelf || busy}
                            title={isSelf ? 'Không thể tự khoá tài khoản của mình' : undefined}
                            onClick={() => void handleToggleStatus(item)}
                          >
                            {item.banned ? 'Mở khoá' : 'Khoá'}
                          </Button>
                          <Button
                            size="sm"
                            variant="ghost"
                            disabled={isSelf || busy}
                            title={isSelf ? 'Không thể tự xoá tài khoản của mình' : undefined}
                            onClick={() => void handleDelete(item)}
                          >
                            Xoá
                          </Button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        {!isLoading && users.length > 0 && totalPages > 1 && (
          <div className="ausers__pager">
            <Pagination page={page} totalPages={totalPages} onChange={setPage} />
          </div>
        )}
      </section>

      {createOpen && (
        <CreateUserModal
          onClose={() => setCreateOpen(false)}
          onCreated={() => {
            setCreateOpen(false);
            void load();
          }}
        />
      )}

      {editingUser && (
        <EditUserModal
          user={editingUser}
          isSelf={currentUser?.id === editingUser.id}
          onClose={() => setEditingUser(null)}
          onSaved={() => {
            setEditingUser(null);
            void load();
          }}
        />
      )}

      {roleTargetUser && (
        <RoleModal
          user={roleTargetUser}
          onClose={() => setRoleTargetUser(null)}
          onSaved={() => {
            setRoleTargetUser(null);
            void load();
          }}
        />
      )}
    </>
  );
};

const CreateUserModal = ({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) => {
  const [form, setForm] = useState<AdminCreateUserPayload>(initialCreateForm);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const toast = useToast();

  const handleSubmit = async () => {
    setIsSubmitting(true);
    setErrorMsg(null);
    try {
      await adminUserApi.createUser({ ...form, phone: form.phone?.trim() || undefined });
      toast.success(`Đã tạo tài khoản ${form.fullName}`);
      onCreated();
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Tạo tài khoản thất bại.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      size="sm"
      title="Thêm tài khoản mới"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={isSubmitting}>
            Đóng
          </Button>
          <Button
            variant="primary"
            loading={isSubmitting}
            disabled={!form.fullName.trim() || !form.email.trim() || form.password.length < 6}
            onClick={handleSubmit}
          >
            Tạo tài khoản
          </Button>
        </>
      }
    >
      {errorMsg && (
        <div className="alert-banner alert-error" role="alert">
          <XCircle size={17} />
          <div>{errorMsg}</div>
        </div>
      )}

      <div className="ausers__form">
        <Input
          label="Họ và tên"
          required
          placeholder="Ví dụ: Nguyễn Văn Hoàng"
          value={form.fullName}
          onChange={(event) => setForm({ ...form, fullName: event.target.value })}
        />
        <Input
          label="Email"
          type="email"
          required
          placeholder="staff@banhmyking.vn"
          value={form.email}
          onChange={(event) => setForm({ ...form, email: event.target.value })}
        />
        <Input
          label="Mật khẩu khởi tạo"
          type="password"
          required
          hint="Tối thiểu 6 ký tự"
          value={form.password}
          onChange={(event) => setForm({ ...form, password: event.target.value })}
        />
        <Input
          label="Số điện thoại"
          type="tel"
          placeholder="0901234567"
          value={form.phone ?? ''}
          onChange={(event) => setForm({ ...form, phone: event.target.value })}
        />
        <Select
          label="Vai trò"
          hint="Cấp quyền ADMIN cho nhân sự qua nút “Vai trò” trong bảng."
          value={form.role}
          onChange={(event) => setForm({ ...form, role: event.target.value as RoleName })}
        >
          {CREATABLE_ROLES.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </Select>
      </div>
    </Modal>
  );
};

const EditUserModal = ({
  user,
  isSelf,
  onClose,
  onSaved,
}: {
  user: AdminUser;
  isSelf: boolean;
  onClose: () => void;
  onSaved: () => void;
}) => {
  const [form, setForm] = useState<AdminUpdateUserPayload>({
    fullName: user.fullName,
    phone: user.phone ?? '',
    role: user.role,
    banned: user.banned,
    password: '',
  });
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const toast = useToast();

  const handleSubmit = async () => {
    setIsSubmitting(true);
    setErrorMsg(null);
    try {
      await adminUserApi.updateUser(user.id, {
        fullName: form.fullName,
        phone: form.phone?.trim() || undefined,
        role: form.role,
        banned: form.banned,
        password: form.password ? form.password : undefined,
      });
      toast.success(`Đã cập nhật tài khoản ${form.fullName}`);
      onSaved();
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Cập nhật tài khoản thất bại.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      size="sm"
      title={`Sửa tài khoản — ${user.fullName}`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={isSubmitting}>
            Đóng
          </Button>
          <Button
            variant="primary"
            loading={isSubmitting}
            disabled={!form.fullName.trim()}
            onClick={handleSubmit}
          >
            Lưu thay đổi
          </Button>
        </>
      }
    >
      {errorMsg && (
        <div className="alert-banner alert-error" role="alert">
          <XCircle size={17} />
          <div>{errorMsg}</div>
        </div>
      )}

      <div className="ausers__form">
        <Input label="Email" value={user.email} disabled />

        <Input
          label="Họ và tên"
          required
          value={form.fullName}
          onChange={(event) => setForm({ ...form, fullName: event.target.value })}
        />

        <Input
          label="Số điện thoại"
          type="tel"
          value={form.phone ?? ''}
          onChange={(event) => setForm({ ...form, phone: event.target.value })}
        />

        <Select
          label="Vai trò"
          value={form.role}
          disabled={isSelf}
          hint={isSelf ? 'Bạn không thể tự đổi vai trò của chính mình.' : undefined}
          onChange={(event) => setForm({ ...form, role: event.target.value as RoleName })}
        >
          {ROLE_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </Select>

        <Input
          label="Đặt lại mật khẩu"
          type="password"
          hint="Để trống nếu không đổi mật khẩu"
          value={form.password ?? ''}
          onChange={(event) => setForm({ ...form, password: event.target.value })}
        />

        <label className="ausers__check">
          <input
            type="checkbox"
            checked={form.banned ?? false}
            disabled={isSelf}
            onChange={(event) => setForm({ ...form, banned: event.target.checked })}
          />
          <span>
            Khoá tài khoản
            {isSelf && <em> — không thể tự khoá chính mình</em>}
          </span>
        </label>
      </div>
    </Modal>
  );
};

const RoleModal = ({ user, onClose, onSaved }: { user: AdminUser; onClose: () => void; onSaved: () => void }) => {
  const [role, setRole] = useState<RoleName>(user.role);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const toast = useToast();

  const handleSubmit = async () => {
    setIsSubmitting(true);
    setErrorMsg(null);
    try {
      await adminUserApi.changeRole(user.id, role);
      toast.success(`Đã đổi vai trò ${user.fullName} thành ${ROLE_LABEL[role]}`);
      onSaved();
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Đổi vai trò thất bại.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      size="sm"
      title="Phân quyền vai trò"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={isSubmitting}>
            Đóng
          </Button>
          <Button
            variant="primary"
            loading={isSubmitting}
            disabled={role === user.role}
            onClick={handleSubmit}
          >
            Cập nhật vai trò
          </Button>
        </>
      }
    >
      {errorMsg && (
        <div className="alert-banner alert-error" role="alert">
          <XCircle size={17} />
          <div>{errorMsg}</div>
        </div>
      )}

      <div className="ausers__form">
        <p className="ausers__role-info">
          Tài khoản <strong>{user.fullName}</strong> ({user.email}) hiện là{' '}
          <Badge tone={ROLE_TONE[user.role] ?? 'neutral'}>{ROLE_LABEL[user.role] ?? user.role}</Badge>
        </p>

        <Select
          label="Vai trò mới"
          value={role}
          hint="Đổi vai trò sẽ thu hồi các phiên đăng nhập cũ để áp dụng quyền mới."
          onChange={(event) => setRole(event.target.value as RoleName)}
        >
          {ROLE_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </Select>
      </div>
    </Modal>
  );
};
