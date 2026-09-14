import React, { useState, useEffect, useCallback } from 'react';
import { AdminLayout } from '../../components/admin/AdminLayout';
import { adminUserApi } from '../../api/adminUserApi';
import type {
  AdminUser,
  AdminCreateUserPayload,
  AdminUpdateUserPayload,
} from '../../types/admin';
import type { RoleName } from '../../types/auth';
import { useAuth } from '../../context/useAuth';

export const AdminUsersPage: React.FC = () => {
  const { user: currentUser } = useAuth();

  // State
  const [users, setUsers] = useState<AdminUser[]>([]);
  const [totalElements, setTotalElements] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(0);
  const [page, setPage] = useState<number>(0);
  const pageSize = 10;

  // Filters
  const [keyword, setKeyword] = useState<string>('');
  const [selectedRole, setSelectedRole] = useState<string>('ALL');
  const [selectedStatus, setSelectedStatus] = useState<string>('ALL');

  // Loading & Alert
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [alert, setAlert] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  // Modals
  const [showCreateModal, setShowCreateModal] = useState<boolean>(false);
  const [showEditModal, setShowEditModal] = useState<boolean>(false);
  const [showRoleModal, setShowRoleModal] = useState<boolean>(false);
  const [editingUser, setEditingUser] = useState<AdminUser | null>(null);
  const [roleTargetUser, setRoleTargetUser] = useState<AdminUser | null>(null);

  // Form states
  const [createForm, setCreateForm] = useState<AdminCreateUserPayload>({
    email: '',
    password: '',
    fullName: '',
    phone: '',
    role: 'STAFF',
  });

  const [editForm, setEditForm] = useState<AdminUpdateUserPayload>({
    fullName: '',
    phone: '',
    role: 'STAFF',
    banned: false,
    password: '',
  });

  const [selectedRoleToAssign, setSelectedRoleToAssign] = useState<RoleName>('STAFF');
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);

  // Fetch Users
  const fetchUsers = useCallback(async () => {
    setIsLoading(true);
    try {
      const res = await adminUserApi.getUsers({
        page,
        size: pageSize,
        keyword: keyword.trim() ? keyword.trim() : undefined,
        role: selectedRole !== 'ALL' ? (selectedRole as RoleName) : undefined,
        banned: selectedStatus === 'ALL' ? undefined : selectedStatus === 'BANNED',
      });
      setUsers(res.content);
      setTotalElements(res.totalElements);
      setTotalPages(res.totalPages);
    } catch (err: unknown) {
      const error = err as Error;
      setAlert({ type: 'error', message: error.message || 'Không thể tải danh sách tài khoản.' });
    } finally {
      setIsLoading(false);
    }
  }, [page, keyword, selectedRole, selectedStatus]);

  useEffect(() => {
    let isMounted = true;
    adminUserApi.getUsers({
      page,
      size: pageSize,
      keyword: keyword.trim() ? keyword.trim() : undefined,
      role: selectedRole !== 'ALL' ? (selectedRole as RoleName) : undefined,
      banned: selectedStatus === 'ALL' ? undefined : selectedStatus === 'BANNED',
    })
      .then((res) => {
        if (isMounted) {
          setUsers(res.content);
          setTotalElements(res.totalElements);
          setTotalPages(res.totalPages);
          setIsLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (isMounted) {
          const error = err as Error;
          setAlert({ type: 'error', message: error.message || 'Không thể tải danh sách tài khoản.' });
          setIsLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, [page, keyword, selectedRole, selectedStatus]);

  // Handle Search Submit
  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    fetchUsers();
  };

  // Open Edit Modal
  const openEditModal = (target: AdminUser) => {
    setEditingUser(target);
    setEditForm({
      fullName: target.fullName,
      phone: target.phone || '',
      role: target.role,
      banned: target.banned,
      password: '',
    });
    setShowEditModal(true);
  };

  // Open Quick Role Assignment Modal
  const openRoleModal = (target: AdminUser) => {
    setRoleTargetUser(target);
    setSelectedRoleToAssign(target.role);
    setShowRoleModal(true);
  };

  // Create User Submit
  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    setAlert(null);
    try {
      await adminUserApi.createUser(createForm);
      setAlert({ type: 'success', message: `Đã tạo mới tài khoản "${createForm.fullName}" (${createForm.role}) thành công!` });
      setShowCreateModal(false);
      setCreateForm({ email: '', password: '', fullName: '', phone: '', role: 'STAFF' });
      fetchUsers();
    } catch (err: unknown) {
      const error = err as Error;
      setAlert({ type: 'error', message: error.message || 'Không thể tạo tài khoản.' });
    } finally {
      setIsSubmitting(false);
    }
  };

  // Edit User Submit
  const handleEditSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingUser) return;
    setIsSubmitting(true);
    setAlert(null);
    try {
      await adminUserApi.updateUser(editingUser.id, {
        fullName: editForm.fullName,
        phone: editForm.phone || undefined,
        role: editForm.role,
        banned: editForm.banned,
        password: editForm.password ? editForm.password : undefined,
      });
      setAlert({ type: 'success', message: `Cập nhật thông tin tài khoản "${editForm.fullName}" thành công!` });
      setShowEditModal(false);
      fetchUsers();
    } catch (err: unknown) {
      const error = err as Error;
      setAlert({ type: 'error', message: error.message || 'Cập nhật tài khoản thất bại.' });
    } finally {
      setIsSubmitting(false);
    }
  };

  // Quick Role Assignment Submit
  const handleRoleAssignSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!roleTargetUser) return;
    setIsSubmitting(true);
    setAlert(null);
    try {
      await adminUserApi.changeRole(roleTargetUser.id, selectedRoleToAssign);
      setAlert({
        type: 'success',
        message: `Đã phân quyền tài khoản "${roleTargetUser.fullName}" thành vai trò "${selectedRoleToAssign}"!`,
      });
      setShowRoleModal(false);
      fetchUsers();
    } catch (err: unknown) {
      const error = err as Error;
      setAlert({ type: 'error', message: error.message || 'Phân quyền thất bại.' });
    } finally {
      setIsSubmitting(false);
    }
  };

  // Toggle Lock/Unlock User
  const handleToggleStatus = async (target: AdminUser) => {
    const isLocking = !target.banned;
    const actionText = isLocking ? 'KHOÁ' : 'MỞ KHOÁ';

    if (currentUser?.id === target.id) {
      setAlert({ type: 'error', message: 'Bạn không thể tự khoá tài khoản của chính mình!' });
      return;
    }

    if (
      window.confirm(
        `Bạn có chắc chắn muốn ${actionText} tài khoản "${target.fullName}" (${target.email})?\n${
          isLocking
            ? 'Khi bị khoá, toàn bộ phiên đăng nhập của người dùng sẽ bị chấm dứt ngay lập tức.'
            : 'Sau khi mở khoá, người dùng có thể đăng nhập bình thường.'
        }`
      )
    ) {
      try {
        await adminUserApi.changeStatus(target.id, isLocking);
        setAlert({
          type: 'success',
          message: `Đã ${actionText.toLowerCase()} tài khoản "${target.fullName}" thành công!`,
        });
        fetchUsers();
      } catch (err: unknown) {
        const error = err as Error;
        setAlert({ type: 'error', message: error.message || `${actionText} tài khoản thất bại.` });
      }
    }
  };

  // Soft Delete User
  const handleDeleteUser = async (target: AdminUser) => {
    if (currentUser?.id === target.id) {
      setAlert({ type: 'error', message: 'Bạn không thể tự xoá tài khoản của chính mình!' });
      return;
    }

    if (
      window.confirm(
        `⚠️ CẢNH BÁO: Bạn có chắc chắn muốn XOÁ tài khoản "${target.fullName}" (${target.email})?\nThao tác này sẽ xoá mềm tài khoản và thu hồi toàn bộ token đăng nhập.`
      )
    ) {
      try {
        await adminUserApi.deleteUser(target.id);
        setAlert({
          type: 'success',
          message: `Đã xoá tài khoản "${target.fullName}" thành công!`,
        });
        fetchUsers();
      } catch (err: unknown) {
        const error = err as Error;
        setAlert({ type: 'error', message: error.message || 'Xoá tài khoản thất bại.' });
      }
    }
  };

  // Role Badge Helper
  const renderRoleBadge = (role: RoleName) => {
    switch (role) {
      case 'ADMIN':
        return <span className="role-pill badge-admin">👑 ADMIN</span>;
      case 'STAFF':
        return <span className="role-pill badge-staff">👨‍🍳 STAFF</span>;
      case 'SHIPPER':
        return <span className="role-pill badge-shipper">🛵 SHIPPER</span>;
      case 'CUSTOMER':
      default:
        return <span className="role-pill badge-customer">🛒 CUSTOMER</span>;
    }
  };

  return (
    <AdminLayout
      title="Quản Lý Tài Khoản & Phân Quyền"
      subtitle="Thêm mới, chỉnh sửa thông tin, phân vai trò STAFF/SHIPPER và khoá/mở tài khoản người dùng"
      onRefresh={fetchUsers}
      isRefreshing={isLoading}
    >
      {/* Alert Banner */}
      {alert && (
        <div
          className={`alert-banner ${alert.type === 'success' ? 'alert-success' : 'alert-error'}`}
          style={{ marginBottom: '1.25rem' }}
        >
          <div>{alert.type === 'success' ? '✅' : '⚠️'} {alert.message}</div>
          <button
            type="button"
            className="alert-close-btn"
            onClick={() => setAlert(null)}
          >
            ✕
          </button>
        </div>
      )}

      {/* Control Bar: Search, Filters & Action Button */}
      <div className="users-control-bar">
        <form className="users-search-form" onSubmit={handleSearch}>
          <div className="search-input-wrapper">
            <span className="search-icon">🔍</span>
            <input
              type="text"
              placeholder="Tìm theo email, họ tên người dùng..."
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              className="users-search-input"
            />
          </div>
          <button type="submit" className="btn-secondary">
            Tìm kiếm
          </button>
        </form>

        <div className="users-filter-group">
          <div className="filter-select-wrapper">
            <label className="filter-label">Vai trò:</label>
            <select
              value={selectedRole}
              onChange={(e) => {
                setSelectedRole(e.target.value);
                setPage(0);
              }}
              className="users-filter-select"
            >
              <option value="ALL">Tất cả vai trò</option>
              <option value="CUSTOMER">Khách hàng (CUSTOMER)</option>
              <option value="STAFF">Nhân viên bếp (STAFF)</option>
              <option value="SHIPPER">Giao hàng (SHIPPER)</option>
              <option value="ADMIN">Quản trị viên (ADMIN)</option>
            </select>
          </div>

          <div className="filter-select-wrapper">
            <label className="filter-label">Trạng thái:</label>
            <select
              value={selectedStatus}
              onChange={(e) => {
                setSelectedStatus(e.target.value);
                setPage(0);
              }}
              className="users-filter-select"
            >
              <option value="ALL">Tất cả trạng thái</option>
              <option value="ACTIVE">Đang hoạt động</option>
              <option value="BANNED">Đã bị khoá</option>
            </select>
          </div>

          <button
            type="button"
            className="btn-primary btn-add-user"
            onClick={() => setShowCreateModal(true)}
          >
            <span>➕</span>
            <span>Thêm tài khoản mới</span>
          </button>
        </div>
      </div>

      {/* Users Table Card */}
      <div className="users-table-card">
        <div className="table-responsive">
          <table className="admin-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Người dùng</th>
                <th>Số điện thoại</th>
                <th>Vai trò (Role)</th>
                <th>Trạng thái</th>
                <th>Ngày tạo</th>
                <th style={{ textAlign: 'right' }}>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {isLoading && users.length === 0 ? (
                <tr>
                  <td colSpan={7} className="table-empty-row">
                    <div className="spinner-royal"></div>
                    <p style={{ marginTop: '0.5rem' }}>Đang tải danh sách người dùng...</p>
                  </td>
                </tr>
              ) : users.length === 0 ? (
                <tr>
                  <td colSpan={7} className="table-empty-row">
                    <span style={{ fontSize: '2rem' }}>👥</span>
                    <p>Không tìm thấy tài khoản nào khớp với điều kiện lọc.</p>
                  </td>
                </tr>
              ) : (
                users.map((item) => {
                  const isSelf = currentUser?.id === item.id;

                  return (
                    <tr key={item.id} className={item.banned ? 'row-banned' : ''}>
                      <td className="col-id">#{item.id}</td>
                      <td>
                        <div className="user-profile-cell">
                          <div className={`user-table-avatar ${item.role.toLowerCase()}`}>
                            {item.fullName ? item.fullName.charAt(0).toUpperCase() : 'U'}
                          </div>
                          <div className="user-table-details">
                            <span className="user-table-name">
                              {item.fullName} {isSelf && <span className="badge-you">(Bạn)</span>}
                            </span>
                            <span className="user-table-email">{item.email}</span>
                          </div>
                        </div>
                      </td>
                      <td>{item.phone || <span className="text-muted">Chưa có</span>}</td>
                      <td>
                        <div className="role-cell-wrapper">
                          {renderRoleBadge(item.role)}
                          {!isSelf && (
                            <button
                              type="button"
                              className="btn-quick-role"
                              onClick={() => openRoleModal(item)}
                              title="Phân quyền vai trò mới"
                            >
                              ⚙️ Đổi
                            </button>
                          )}
                        </div>
                      </td>
                      <td>
                        {item.banned ? (
                          <span className="status-badge banned">🔒 Đã bị khoá</span>
                        ) : (
                          <span className="status-badge active">🟢 Hoạt động</span>
                        )}
                      </td>
                      <td className="col-date">
                        {item.createdAt ? new Date(item.createdAt).toLocaleDateString('vi-VN') : '---'}
                      </td>
                      <td style={{ textAlign: 'right' }}>
                        <div className="action-button-group">
                          {/* Edit Button */}
                          <button
                            type="button"
                            className="btn-action-edit"
                            onClick={() => openEditModal(item)}
                            title="Chỉnh sửa thông tin"
                          >
                            ✏️ Sửa
                          </button>

                          {/* Lock / Unlock Toggle Button */}
                          <button
                            type="button"
                            className={`btn-action-toggle ${item.banned ? 'unlock' : 'lock'}`}
                            onClick={() => handleToggleStatus(item)}
                            disabled={isSelf}
                            title={isSelf ? 'Không thể khoá tài khoản chính mình' : item.banned ? 'Mở khoá tài khoản' : 'Khoá tài khoản'}
                          >
                            {item.banned ? '🔓 Mở' : '🔒 Khoá'}
                          </button>

                          {/* Delete Button */}
                          <button
                            type="button"
                            className="btn-action-delete"
                            onClick={() => handleDeleteUser(item)}
                            disabled={isSelf}
                            title={isSelf ? 'Không thể xoá tài khoản chính mình' : 'Xoá tài khoản'}
                          >
                            🗑️
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination Bar */}
        <div className="table-pagination-bar">
          <div className="pagination-info">
            Hiển thị <strong>{users.length}</strong> / <strong>{totalElements}</strong> tài khoản (Trang {page + 1}/{Math.max(totalPages, 1)})
          </div>

          <div className="pagination-controls">
            <button
              type="button"
              className="btn-page"
              disabled={page === 0 || isLoading}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              ← Trang trước
            </button>

            {Array.from({ length: Math.min(totalPages, 5) }, (_, i) => {
              const pageIdx = i;
              return (
                <button
                  key={pageIdx}
                  type="button"
                  className={`btn-page-number ${page === pageIdx ? 'active' : ''}`}
                  onClick={() => setPage(pageIdx)}
                >
                  {pageIdx + 1}
                </button>
              );
            })}

            <button
              type="button"
              className="btn-page"
              disabled={page >= totalPages - 1 || isLoading}
              onClick={() => setPage((p) => p + 1)}
            >
              Trang sau →
            </button>
          </div>
        </div>
      </div>

      {/* ─── MODAL: TẠO TÀI KHOẢN MỚI ─── */}
      {showCreateModal && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <div className="modal-header">
              <h3 className="modal-title">➕ Thêm Tài Khoản Mới</h3>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setShowCreateModal(false)}
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleCreateSubmit}>
              <div className="modal-body">
                <div className="form-group">
                  <label className="form-label">
                    Họ và tên <span className="req">*</span>
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="Ví dụ: Nguyễn Văn Hoàng"
                    value={createForm.fullName}
                    onChange={(e) => setCreateForm({ ...createForm, fullName: e.target.value })}
                    className="form-input"
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">
                    Địa chỉ Email <span className="req">*</span>
                  </label>
                  <input
                    type="email"
                    required
                    placeholder="Ví dụ: staff@banhmyking.vn"
                    value={createForm.email}
                    onChange={(e) => setCreateForm({ ...createForm, email: e.target.value })}
                    className="form-input"
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">
                    Mật khẩu khởi tạo <span className="req">*</span>
                  </label>
                  <input
                    type="password"
                    required
                    minLength={6}
                    placeholder="Tối thiểu 6 ký tự"
                    value={createForm.password}
                    onChange={(e) => setCreateForm({ ...createForm, password: e.target.value })}
                    className="form-input"
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">Số điện thoại liên hệ</label>
                  <input
                    type="tel"
                    placeholder="0901234567"
                    value={createForm.phone || ''}
                    onChange={(e) => setCreateForm({ ...createForm, phone: e.target.value })}
                    className="form-input"
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">
                    Phân quyền vai trò <span className="req">*</span>
                  </label>
                  <select
                    value={createForm.role}
                    onChange={(e) =>
                      setCreateForm({ ...createForm, role: e.target.value as RoleName })
                    }
                    className="form-select"
                  >
                    <option value="STAFF">👨‍🍳 Nhân viên bếp/quầy (STAFF)</option>
                    <option value="SHIPPER">🛵 Nhân viên giao hàng (SHIPPER)</option>
                    <option value="CUSTOMER">🛒 Khách hàng thông thường (CUSTOMER)</option>
                  </select>
                  <span className="form-hint">
                    Phân quyền nội bộ cho phép nhân viên vận hành các quy trình bếp hoặc giao nhận.
                  </span>
                </div>
              </div>

              <div className="modal-footer">
                <button
                  type="button"
                  className="btn-outline"
                  onClick={() => setShowCreateModal(false)}
                >
                  Huỷ bỏ
                </button>
                <button type="submit" className="btn-primary" disabled={isSubmitting}>
                  {isSubmitting ? 'Đang tạo...' : 'Tạo tài khoản'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ─── MODAL: CHỈNH SỬA TÀI KHOẢN ─── */}
      {showEditModal && editingUser && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <div className="modal-header">
              <h3 className="modal-title">✏️ Chỉnh Sửa Tài Khoản #{editingUser.id}</h3>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setShowEditModal(false)}
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleEditSubmit}>
              <div className="modal-body">
                <div className="form-group">
                  <label className="form-label">Địa chỉ Email (Không đổi)</label>
                  <input
                    type="text"
                    disabled
                    value={editingUser.email}
                    className="form-input form-input-disabled"
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">
                    Họ và tên <span className="req">*</span>
                  </label>
                  <input
                    type="text"
                    required
                    value={editForm.fullName || ''}
                    onChange={(e) => setEditForm({ ...editForm, fullName: e.target.value })}
                    className="form-input"
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">Số điện thoại</label>
                  <input
                    type="tel"
                    value={editForm.phone || ''}
                    onChange={(e) => setEditForm({ ...editForm, phone: e.target.value })}
                    className="form-input"
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">Phân quyền vai trò</label>
                  <select
                    value={editForm.role}
                    disabled={currentUser?.id === editingUser.id}
                    onChange={(e) =>
                      setEditForm({ ...editForm, role: e.target.value as RoleName })
                    }
                    className="form-select"
                  >
                    <option value="CUSTOMER">Khách hàng (CUSTOMER)</option>
                    <option value="STAFF">Nhân viên bếp (STAFF)</option>
                    <option value="SHIPPER">Giao hàng (SHIPPER)</option>
                    <option value="ADMIN">Quản trị viên (ADMIN)</option>
                  </select>
                  {currentUser?.id === editingUser.id && (
                    <span className="form-hint" style={{ color: 'var(--amber-700)' }}>
                      ⚠️ Bạn không thể tự thay đổi vai trò ADMIN của chính mình.
                    </span>
                  )}
                </div>

                <div className="form-group">
                  <label className="form-label">Đặt lại mật khẩu mới (Để trống nếu không đổi)</label>
                  <input
                    type="password"
                    placeholder="Nhập mật khẩu mới nếu muốn reset..."
                    value={editForm.password || ''}
                    onChange={(e) => setEditForm({ ...editForm, password: e.target.value })}
                    className="form-input"
                  />
                </div>

                <div className="form-group">
                  <label className="checkbox-label">
                    <input
                      type="checkbox"
                      checked={editForm.banned || false}
                      disabled={currentUser?.id === editingUser.id}
                      onChange={(e) => setEditForm({ ...editForm, banned: e.target.checked })}
                    />
                    <span>Khoá tài khoản (Banned)</span>
                  </label>
                  {currentUser?.id === editingUser.id && (
                    <span className="form-hint" style={{ color: 'var(--amber-700)' }}>
                      ⚠️ Không thể khoá tài khoản chính mình.
                    </span>
                  )}
                </div>
              </div>

              <div className="modal-footer">
                <button
                  type="button"
                  className="btn-outline"
                  onClick={() => setShowEditModal(false)}
                >
                  Huỷ bỏ
                </button>
                <button type="submit" className="btn-primary" disabled={isSubmitting}>
                  {isSubmitting ? 'Đang lưu...' : 'Lưu thay đổi'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ─── MODAL: PHÂN QUYỀN NHANH (ROLE ASSIGNMENT) ─── */}
      {showRoleModal && roleTargetUser && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <div className="modal-header">
              <h3 className="modal-title">👑 Phân Quyền Vai Trò (Role Assignment)</h3>
              <button
                type="button"
                className="modal-close-btn"
                onClick={() => setShowRoleModal(false)}
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleRoleAssignSubmit}>
              <div className="modal-body">
                <div className="role-assignment-info">
                  <p>
                    Bạn đang phân quyền cho tài khoản: <strong>{roleTargetUser.fullName}</strong>
                  </p>
                  <p style={{ fontSize: '0.875rem', color: 'var(--stone-600)' }}>
                    Email: <code>{roleTargetUser.email}</code> | Vai trò hiện tại: {renderRoleBadge(roleTargetUser.role)}
                  </p>
                </div>

                <div className="form-group" style={{ marginTop: '1.25rem' }}>
                  <label className="form-label">Chọn vai trò mới:</label>
                  <div className="role-options-list">
                    <label className={`role-option-card ${selectedRoleToAssign === 'STAFF' ? 'selected' : ''}`}>
                      <input
                        type="radio"
                        name="roleChoice"
                        value="STAFF"
                        checked={selectedRoleToAssign === 'STAFF'}
                        onChange={() => setSelectedRoleToAssign('STAFF')}
                      />
                      <div className="role-option-body">
                        <span className="role-option-title">👨‍🍳 Nhân viên (STAFF)</span>
                        <span className="role-option-desc">Có quyền xử lý đơn hàng tại quầy và theo dõi đơn bếp.</span>
                      </div>
                    </label>

                    <label className={`role-option-card ${selectedRoleToAssign === 'SHIPPER' ? 'selected' : ''}`}>
                      <input
                        type="radio"
                        name="roleChoice"
                        value="SHIPPER"
                        checked={selectedRoleToAssign === 'SHIPPER'}
                        onChange={() => setSelectedRoleToAssign('SHIPPER')}
                      />
                      <div className="role-option-body">
                        <span className="role-option-title">🛵 Giao hàng (SHIPPER)</span>
                        <span className="role-option-desc">Có quyền nhận đơn vận chuyển và cập nhật tiến trình giao hàng.</span>
                      </div>
                    </label>

                    <label className={`role-option-card ${selectedRoleToAssign === 'CUSTOMER' ? 'selected' : ''}`}>
                      <input
                        type="radio"
                        name="roleChoice"
                        value="CUSTOMER"
                        checked={selectedRoleToAssign === 'CUSTOMER'}
                        onChange={() => setSelectedRoleToAssign('CUSTOMER')}
                      />
                      <div className="role-option-body">
                        <span className="role-option-title">🛒 Khách hàng (CUSTOMER)</span>
                        <span className="role-option-desc">Tài khoản mua hàng tiêu chuẩn.</span>
                      </div>
                    </label>

                    <label className={`role-option-card ${selectedRoleToAssign === 'ADMIN' ? 'selected' : ''}`}>
                      <input
                        type="radio"
                        name="roleChoice"
                        value="ADMIN"
                        checked={selectedRoleToAssign === 'ADMIN'}
                        onChange={() => setSelectedRoleToAssign('ADMIN')}
                      />
                      <div className="role-option-body">
                        <span className="role-option-title">👑 Quản trị viên (ADMIN)</span>
                        <span className="role-option-desc">Toàn quyền kiểm soát hệ thống, thống kê doanh thu và phân quyền.</span>
                      </div>
                    </label>
                  </div>

                  <div className="role-change-notice">
                    ℹ️ Khi thay đổi vai trò, các phiên đăng nhập cũ của tài khoản này sẽ tự động được thu hồi để áp dụng quyền mới.
                  </div>
                </div>
              </div>

              <div className="modal-footer">
                <button
                  type="button"
                  className="btn-outline"
                  onClick={() => setShowRoleModal(false)}
                >
                  Huỷ bỏ
                </button>
                <button type="submit" className="btn-primary" disabled={isSubmitting}>
                  {isSubmitting ? 'Đang cấp quyền...' : 'Xác nhận cấp quyền'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </AdminLayout>
  );
};
