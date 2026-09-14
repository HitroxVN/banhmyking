import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { StaffLayout } from '../../components/staff/StaffLayout';
import { staffCatalogApi } from '../../api/staffCatalogApi';
import type { CategoryItem, ProductItem, ProductCreatePayload, ProductUpdatePayload } from '../../types/staff';
import { formatCurrency } from '../../utils/formatters';

export const StaffMenuPage: React.FC = () => {
  const [categories, setCategories] = useState<CategoryItem[]>([]);
  const [products, setProducts] = useState<ProductItem[]>([]);
  const [selectedCategoryId, setSelectedCategoryId] = useState<number | null>(null);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);
  const [alert, setAlert] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  // Modal States
  const [showCreateModal, setShowCreateModal] = useState<boolean>(false);
  const [showEditModal, setShowEditModal] = useState<boolean>(false);
  const [editingProduct, setEditingProduct] = useState<ProductItem | null>(null);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);

  // Form States
  const [createForm, setCreateForm] = useState<ProductCreatePayload>({
    categoryId: 1,
    name: '',
    description: '',
    imageUrl: '',
    price: 35000,
    available: true,
    featured: false,
  });

  const [editForm, setEditForm] = useState<ProductUpdatePayload>({
    categoryId: 1,
    name: '',
    description: '',
    imageUrl: '',
    price: 35000,
    available: true,
    featured: false,
  });

  // Image Upload States
  const [isUploadingImage, setIsUploadingImage] = useState<boolean>(false);
  const [showManualUrlCreate, setShowManualUrlCreate] = useState<boolean>(false);
  const [showManualUrlEdit, setShowManualUrlEdit] = useState<boolean>(false);
  const [isDragOverCreate, setIsDragOverCreate] = useState<boolean>(false);
  const [isDragOverEdit, setIsDragOverEdit] = useState<boolean>(false);

  // Xử lý tải ảnh lên máy chủ
  const handleUploadFile = async (
    file: File | undefined,
    isEdit: boolean
  ) => {
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      setAlert({
        type: 'error',
        message: 'Tệp tải lên phải là định dạng hình ảnh (PNG, JPG, WEBP, GIF).',
      });
      return;
    }

    if (file.size > 5 * 1024 * 1024) {
      setAlert({
        type: 'error',
        message: 'Dung lượng ảnh tải lên không được vượt quá 5MB.',
      });
      return;
    }

    setIsUploadingImage(true);
    try {
      const uploadedUrl = await staffCatalogApi.uploadImage(file);
      if (isEdit) {
        setEditForm((prev) => ({ ...prev, imageUrl: uploadedUrl }));
      } else {
        setCreateForm((prev) => ({ ...prev, imageUrl: uploadedUrl }));
      }
      setAlert({
        type: 'success',
        message: 'Tải ảnh món ăn lên máy chủ thành công!',
      });
    } catch (err: unknown) {
      const error = err as Error;
      setAlert({
        type: 'error',
        message: error.message || 'Tải ảnh lên máy chủ thất bại.',
      });
    } finally {
      setIsUploadingImage(false);
    }
  };

  const handleDropFile = (e: React.DragEvent, isEdit: boolean) => {
    e.preventDefault();
    if (isEdit) setIsDragOverEdit(false);
    else setIsDragOverCreate(false);

    const file = e.dataTransfer.files?.[0];
    handleUploadFile(file, isEdit);
  };


  const loadData = useCallback(async (manual = false) => {
    if (manual) {
      setIsRefreshing(true);
    }
    try {
      const [catList, prodList] = await Promise.all([
        staffCatalogApi.getCategories(),
        staffCatalogApi.getProducts(undefined, false), // lấy cả món còn lẫn hết
      ]);
      setCategories(catList);
      setProducts(prodList);
      if (catList.length > 0 && !createForm.categoryId) {
        setCreateForm((prev) => ({ ...prev, categoryId: catList[0].id }));
      }
    } catch (err: unknown) {
      const error = err as Error;
      setAlert({ type: 'error', message: error.message || 'Không thể tải dữ liệu thực đơn.' });
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, [createForm.categoryId]);

  useEffect(() => {
    let ignore = false;
    staffCatalogApi
      .getCategories()
      .then((catList) => {
        if (!ignore) {
          setCategories(catList);
          if (catList.length > 0) {
            setCreateForm((prev) => ({ ...prev, categoryId: catList[0].id }));
          }
        }
        return staffCatalogApi.getProducts(undefined, false);
      })
      .then((prodList) => {
        if (!ignore) {
          setProducts(prodList);
          setIsLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (!ignore) {
          const error = err as Error;
          setAlert({ type: 'error', message: error.message || 'Không thể tải dữ liệu thực đơn.' });
          setIsLoading(false);
        }
      });

    return () => {
      ignore = true;
    };
  }, []);

  // Lọc sản phẩm theo danh mục và từ khoá tìm kiếm
  const filteredProducts = useMemo(() => {
    return products.filter((p) => {
      const matchCategory = selectedCategoryId ? p.categoryId === selectedCategoryId : true;
      const matchQuery = searchQuery.trim()
        ? p.name.toLowerCase().includes(searchQuery.toLowerCase().trim()) ||
          (p.description && p.description.toLowerCase().includes(searchQuery.toLowerCase().trim()))
        : true;
      return matchCategory && matchQuery;
    });
  }, [products, selectedCategoryId, searchQuery]);

  // Thao tác bật/tắt nhanh tình trạng còn hàng / hết hàng (1 chạm)
  const handleToggleAvailable = async (prod: ProductItem) => {
    try {
      const updated = await staffCatalogApi.toggleProductAvailability(prod);
      setProducts((prev) => prev.map((p) => (p.id === updated.id ? updated : p)));
      setAlert({
        type: 'success',
        message: `Đã cập nhật tình trạng "${updated.name}" thành: ${
          updated.available ? '🟢 CÒN HÀNG' : '🔴 HẾT HÀNG'
        }`,
      });
    } catch (err: unknown) {
      const error = err as Error;
      setAlert({ type: 'error', message: error.message || 'Cập nhật trạng thái thất bại.' });
    }
  };

  // Mở modal chỉnh sửa
  const handleOpenEdit = (prod: ProductItem) => {
    setEditingProduct(prod);
    setEditForm({
      categoryId: prod.categoryId,
      name: prod.name,
      description: prod.description || '',
      imageUrl: prod.imageUrl || '',
      price: prod.price,
      available: prod.available,
      featured: prod.featured,
    });
    setShowEditModal(true);
  };

  // Submit tạo món mới
  const handleCreateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    setAlert(null);
    try {
      const created = await staffCatalogApi.createProduct(createForm);
      setProducts((prev) => [created, ...prev]);
      setAlert({ type: 'success', message: `Đã thêm món "${created.name}" vào thực đơn thành công!` });
      setShowCreateModal(false);
      setCreateForm({
        categoryId: categories.length > 0 ? categories[0].id : 1,
        name: '',
        description: '',
        imageUrl: '',
        price: 35000,
        available: true,
        featured: false,
      });
    } catch (err: unknown) {
      const error = err as Error;
      setAlert({ type: 'error', message: error.message || 'Thêm món ăn thất bại.' });
    } finally {
      setIsSubmitting(false);
    }
  };

  // Submit chỉnh sửa món
  const handleEditSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingProduct) return;
    setIsSubmitting(true);
    setAlert(null);
    try {
      const updated = await staffCatalogApi.updateProduct(editingProduct.id, editForm);
      setProducts((prev) => prev.map((p) => (p.id === updated.id ? updated : p)));
      setAlert({ type: 'success', message: `Cập nhật thông tin món "${updated.name}" thành công!` });
      setShowEditModal(false);
    } catch (err: unknown) {
      const error = err as Error;
      setAlert({ type: 'error', message: error.message || 'Cập nhật món ăn thất bại.' });
    } finally {
      setIsSubmitting(false);
    }
  };

  // Xoá món ăn
  const handleDeleteProduct = async (prod: ProductItem) => {
    if (
      window.confirm(
        `Bạn có chắc chắn muốn xoá món "${prod.name}" khỏi thực đơn?\nMón ăn sẽ không còn hiển thị cho khách đặt hàng.`
      )
    ) {
      try {
        await staffCatalogApi.deleteProduct(prod.id);
        setProducts((prev) => prev.filter((p) => p.id !== prod.id));
        setAlert({ type: 'success', message: `Đã xoá món "${prod.name}" khỏi thực đơn thành công!` });
      } catch (err: unknown) {
        const error = err as Error;
        setAlert({ type: 'error', message: error.message || 'Xoá món ăn thất bại.' });
      }
    }
  };

  return (
    <StaffLayout
      title="Quản Lý Thực Đơn (Menu Bếp)"
      subtitle="Thêm, sửa, xoá, cập nhật giá và bật/tắt tình trạng còn hàng / hết hàng của từng món"
      onRefresh={() => loadData(true)}
      isRefreshing={isRefreshing}
    >
      {/* Alert Banner */}
      {alert && (
        <div
          className={`alert-banner ${alert.type === 'success' ? 'alert-success' : 'alert-error'}`}
          style={{ marginBottom: '1.25rem' }}
        >
          <div>{alert.type === 'success' ? '✅' : '⚠️'} {alert.message}</div>
          <button type="button" className="alert-close-btn" onClick={() => setAlert(null)}>
            ✕
          </button>
        </div>
      )}

      {/* Control Bar: Categories Filter, Search & Add Button */}
      <div className="menu-control-bar">
        {/* Category Tabs */}
        <div className="category-tabs-row">
          <button
            type="button"
            className={`cat-tab-btn ${selectedCategoryId === null ? 'active' : ''}`}
            onClick={() => setSelectedCategoryId(null)}
          >
            Tất cả ({products.length})
          </button>
          {categories.map((c) => {
            const count = products.filter((p) => p.categoryId === c.id).length;
            return (
              <button
                key={c.id}
                type="button"
                className={`cat-tab-btn ${selectedCategoryId === c.id ? 'active' : ''}`}
                onClick={() => setSelectedCategoryId(c.id)}
              >
                {c.name} ({count})
              </button>
            );
          })}
        </div>

        {/* Search & Actions */}
        <div className="menu-actions-row">
          <div className="search-input-wrapper" style={{ minWidth: '260px' }}>
            <span className="search-icon">🔍</span>
            <input
              type="text"
              placeholder="Tìm món theo tên..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="users-search-input"
            />
          </div>

          <button
            type="button"
            className="btn-primary"
            style={{ width: 'auto', padding: '0.65rem 1.25rem' }}
            onClick={() => setShowCreateModal(true)}
          >
            ➕ Thêm món mới
          </button>
        </div>
      </div>

      {/* Product Grid */}
      {isLoading && products.length === 0 ? (
        <div className="dashboard-loading-skeleton">
          <div className="spinner-royal"></div>
          <p>Đang tải danh sách thực đơn...</p>
        </div>
      ) : filteredProducts.length === 0 ? (
        <div className="menu-empty-card">
          <span style={{ fontSize: '3rem' }}>🥖</span>
          <h3>Không tìm thấy món ăn nào</h3>
          <p>Thử đổi điều kiện tìm kiếm hoặc bấm "Thêm món mới" để tạo món cho thực đơn.</p>
        </div>
      ) : (
        <div className="menu-product-grid">
          {filteredProducts.map((prod) => (
            <div key={prod.id} className={`menu-product-card ${!prod.available ? 'is-sold-out' : ''}`}>
              {/* Product Card Image */}
              <div className="menu-card-image-wrap">
                {prod.imageUrl ? (
                  <img src={prod.imageUrl} alt={prod.name} className="menu-card-img" />
                ) : (
                  <div className="menu-card-placeholder">
                    {prod.name.toLowerCase().includes('nước') || prod.name.toLowerCase().includes('trà')
                      ? '🥤'
                      : '🥖'}
                  </div>
                )}
                <span className="menu-card-cat-badge">{prod.categoryName || 'Món ăn'}</span>
                {prod.featured && <span className="menu-card-featured-badge">⭐ Bán chạy</span>}
              </div>

              {/* Product Info */}
              <div className="menu-card-body">
                <div className="menu-card-header">
                  <h4 className="menu-card-title">{prod.name}</h4>
                  <span className="menu-card-price">{formatCurrency(prod.price)}</span>
                </div>

                <p className="menu-card-desc" title={prod.description}>
                  {prod.description || 'Chưa có mô tả chi tiết món ăn.'}
                </p>

                {/* Instant Availability Toggle Button (1-touch) */}
                <div className="menu-availability-section">
                  <button
                    type="button"
                    className={`btn-stock-toggle ${prod.available ? 'in-stock' : 'out-of-stock'}`}
                    onClick={() => handleToggleAvailable(prod)}
                    title="Bấm để chuyển đổi nhanh tình trạng Còn hàng / Hết hàng"
                  >
                    <span className="stock-dot"></span>
                    <span>{prod.available ? '🟢 Còn hàng (Đang phục vụ)' : '🔴 TẠM HẾT HÀNG'}</span>
                  </button>
                </div>

                {/* Card Bottom Actions */}
                <div className="menu-card-footer">
                  <button
                    type="button"
                    className="btn-menu-action edit"
                    onClick={() => handleOpenEdit(prod)}
                  >
                    ✏️ Sửa món / Giá
                  </button>
                  <button
                    type="button"
                    className="btn-menu-action delete"
                    onClick={() => handleDeleteProduct(prod)}
                    title="Xóa món"
                  >
                    🗑️ Xóa
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* ─── MODAL: THÊM MÓN MỚI ─── */}
      {showCreateModal && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <div className="modal-header">
              <h3 className="modal-title">➕ Thêm Món Ăn Mới</h3>
              <button type="button" className="modal-close-btn" onClick={() => setShowCreateModal(false)}>
                ✕
              </button>
            </div>

            <form onSubmit={handleCreateSubmit}>
              <div className="modal-body">
                <div className="form-group">
                  <label className="form-label">
                    Danh mục món ăn <span className="req">*</span>
                  </label>
                  <select
                    value={createForm.categoryId}
                    onChange={(e) => setCreateForm({ ...createForm, categoryId: Number(e.target.value) })}
                    className="form-select"
                  >
                    {categories.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="form-group">
                  <label className="form-label">
                    Tên món ăn <span className="req">*</span>
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="Ví dụ: Bánh Mì Thập Cẩm Đặc Biệt"
                    value={createForm.name}
                    onChange={(e) => setCreateForm({ ...createForm, name: e.target.value })}
                    className="form-input"
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">
                    Giá bán (VNĐ) <span className="req">*</span>
                  </label>
                  <input
                    type="number"
                    required
                    min={0}
                    step={1000}
                    placeholder="Ví dụ: 35000"
                    value={createForm.price}
                    onChange={(e) => setCreateForm({ ...createForm, price: Number(e.target.value) })}
                    className="form-input"
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">Mô tả món ăn</label>
                  <textarea
                    rows={3}
                    placeholder="Thịt xá xíu, pate gan, dưa góp, rau thơm, sốt bơ trứng đặc trưng..."
                    value={createForm.description}
                    onChange={(e) => setCreateForm({ ...createForm, description: e.target.value })}
                    className="form-input"
                  />
                </div>

                {/* Image Upload Control */}
                <div className="form-group">
                  <label className="form-label">Hình ảnh món ăn</label>
                  <div className="image-uploader-wrapper">
                    <input
                      id="create-image-file-input"
                      type="file"
                      accept="image/*"
                      style={{ display: 'none' }}
                      onChange={(e) => {
                        handleUploadFile(e.target.files?.[0], false);
                        e.target.value = '';
                      }}
                      disabled={isUploadingImage}
                    />

                    {isUploadingImage ? (
                      <div className="image-uploading-indicator">
                        <span className="spinner-mini"></span>
                        <span>Đang tải tệp ảnh lên máy chủ...</span>
                      </div>
                    ) : createForm.imageUrl ? (
                      <div className="image-preview-card">
                        <img
                          src={createForm.imageUrl}
                          alt="Ảnh món ăn"
                          className="image-preview-thumb"
                          onError={(e) => {
                            (e.currentTarget as HTMLImageElement).src =
                              'https://images.unsplash.com/photo-1626804475297-41608ea09aeb?auto=format&fit=crop&w=200&q=80';
                          }}
                        />
                        <div className="image-preview-info">
                          <span className="image-preview-status">✅ Đã có ảnh món ăn</span>
                          <span className="image-preview-url" title={createForm.imageUrl}>
                            {createForm.imageUrl}
                          </span>
                          <div className="image-preview-actions">
                            <button
                              type="button"
                              className="btn-preview-action change"
                              onClick={() => document.getElementById('create-image-file-input')?.click()}
                            >
                              🔄 Đổi ảnh khác
                            </button>
                            <button
                              type="button"
                              className="btn-preview-action remove"
                              onClick={() => setCreateForm((prev) => ({ ...prev, imageUrl: '' }))}
                            >
                              🗑️ Xóa ảnh
                            </button>
                          </div>
                        </div>
                      </div>
                    ) : (
                      <div
                        className={`image-dropzone ${isDragOverCreate ? 'drag-active' : ''}`}
                        onClick={() => document.getElementById('create-image-file-input')?.click()}
                        onDragOver={(e) => {
                          e.preventDefault();
                          setIsDragOverCreate(true);
                        }}
                        onDragLeave={() => setIsDragOverCreate(false)}
                        onDrop={(e) => handleDropFile(e, false)}
                      >
                        <span className="dropzone-icon">📸</span>
                        <span className="dropzone-prompt">Bấm hoặc kéo thả ảnh vào đây để tải lên</span>
                        <span className="dropzone-hint">Hỗ trợ JPG, PNG, WEBP (Tối đa 5MB)</span>
                        <button type="button" className="btn-choose-file">
                          📁 Chọn tệp từ máy tính
                        </button>
                      </div>
                    )}

                    <div style={{ marginTop: '0.4rem' }}>
                      <button
                        type="button"
                        className="image-url-toggle-btn"
                        onClick={() => setShowManualUrlCreate(!showManualUrlCreate)}
                      >
                        {showManualUrlCreate ? 'Ẩn nhập URL thủ công' : '🔗 Hoặc nhập URL ảnh trực tiếp'}
                      </button>
                      {showManualUrlCreate && (
                        <input
                          type="url"
                          placeholder="https://images.unsplash.com/..."
                          value={createForm.imageUrl}
                          onChange={(e) => setCreateForm({ ...createForm, imageUrl: e.target.value })}
                          className="form-input"
                          style={{ marginTop: '0.4rem' }}
                        />
                      )}
                    </div>
                  </div>
                </div>

                <div className="form-group">
                  <label className="checkbox-label">
                    <input
                      type="checkbox"
                      checked={createForm.available}
                      onChange={(e) => setCreateForm({ ...createForm, available: e.target.checked })}
                    />
                    <span>Còn hàng (Có thể phục vụ ngay)</span>
                  </label>
                </div>
              </div>

              <div className="modal-footer">
                <button
                  type="button"
                  className="btn-outline"
                  onClick={() => setShowCreateModal(false)}
                  disabled={isSubmitting}
                >
                  Huỷ bỏ
                </button>
                <button type="submit" className="btn-primary" disabled={isSubmitting}>
                  {isSubmitting ? 'Đang thêm...' : '➕ Thêm vào thực đơn'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ─── MODAL: CHỈNH SỬA MÓN ĂN & GIÁ ─── */}
      {showEditModal && editingProduct && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <div className="modal-header">
              <h3 className="modal-title">✏️ Chỉnh Sửa Món #{editingProduct.id}</h3>
              <button type="button" className="modal-close-btn" onClick={() => setShowEditModal(false)}>
                ✕
              </button>
            </div>

            <form onSubmit={handleEditSubmit}>
              <div className="modal-body">
                <div className="form-group">
                  <label className="form-label">
                    Danh mục <span className="req">*</span>
                  </label>
                  <select
                    value={editForm.categoryId}
                    onChange={(e) => setEditForm({ ...editForm, categoryId: Number(e.target.value) })}
                    className="form-select"
                  >
                    {categories.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="form-group">
                  <label className="form-label">
                    Tên món ăn <span className="req">*</span>
                  </label>
                  <input
                    type="text"
                    required
                    value={editForm.name}
                    onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
                    className="form-input"
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">
                    Giá bán (VNĐ) <span className="req">*</span>
                  </label>
                  <input
                    type="number"
                    required
                    min={0}
                    step={1000}
                    value={editForm.price}
                    onChange={(e) => setEditForm({ ...editForm, price: Number(e.target.value) })}
                    className="form-input"
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">Mô tả món ăn</label>
                  <textarea
                    rows={3}
                    value={editForm.description || ''}
                    onChange={(e) => setEditForm({ ...editForm, description: e.target.value })}
                    className="form-input"
                  />
                </div>

                {/* Image Upload Control */}
                <div className="form-group">
                  <label className="form-label">Hình ảnh món ăn</label>
                  <div className="image-uploader-wrapper">
                    <input
                      id="edit-image-file-input"
                      type="file"
                      accept="image/*"
                      style={{ display: 'none' }}
                      onChange={(e) => {
                        handleUploadFile(e.target.files?.[0], true);
                        e.target.value = '';
                      }}
                      disabled={isUploadingImage}
                    />

                    {isUploadingImage ? (
                      <div className="image-uploading-indicator">
                        <span className="spinner-mini"></span>
                        <span>Đang tải tệp ảnh lên máy chủ...</span>
                      </div>
                    ) : editForm.imageUrl ? (
                      <div className="image-preview-card">
                        <img
                          src={editForm.imageUrl}
                          alt="Ảnh món ăn"
                          className="image-preview-thumb"
                          onError={(e) => {
                            (e.currentTarget as HTMLImageElement).src =
                              'https://images.unsplash.com/photo-1626804475297-41608ea09aeb?auto=format&fit=crop&w=200&q=80';
                          }}
                        />
                        <div className="image-preview-info">
                          <span className="image-preview-status">✅ Đã có ảnh món ăn</span>
                          <span className="image-preview-url" title={editForm.imageUrl}>
                            {editForm.imageUrl}
                          </span>
                          <div className="image-preview-actions">
                            <button
                              type="button"
                              className="btn-preview-action change"
                              onClick={() => document.getElementById('edit-image-file-input')?.click()}
                            >
                              🔄 Đổi ảnh khác
                            </button>
                            <button
                              type="button"
                              className="btn-preview-action remove"
                              onClick={() => setEditForm((prev) => ({ ...prev, imageUrl: '' }))}
                            >
                              🗑️ Xóa ảnh
                            </button>
                          </div>
                        </div>
                      </div>
                    ) : (
                      <div
                        className={`image-dropzone ${isDragOverEdit ? 'drag-active' : ''}`}
                        onClick={() => document.getElementById('edit-image-file-input')?.click()}
                        onDragOver={(e) => {
                          e.preventDefault();
                          setIsDragOverEdit(true);
                        }}
                        onDragLeave={() => setIsDragOverEdit(false)}
                        onDrop={(e) => handleDropFile(e, true)}
                      >
                        <span className="dropzone-icon">📸</span>
                        <span className="dropzone-prompt">Bấm hoặc kéo thả ảnh vào đây để tải lên</span>
                        <span className="dropzone-hint">Hỗ trợ JPG, PNG, WEBP (Tối đa 5MB)</span>
                        <button type="button" className="btn-choose-file">
                          📁 Chọn tệp từ máy tính
                        </button>
                      </div>
                    )}

                    <div style={{ marginTop: '0.4rem' }}>
                      <button
                        type="button"
                        className="image-url-toggle-btn"
                        onClick={() => setShowManualUrlEdit(!showManualUrlEdit)}
                      >
                        {showManualUrlEdit ? 'Ẩn nhập URL thủ công' : '🔗 Hoặc nhập URL ảnh trực tiếp'}
                      </button>
                      {showManualUrlEdit && (
                        <input
                          type="url"
                          placeholder="https://images.unsplash.com/..."
                          value={editForm.imageUrl || ''}
                          onChange={(e) => setEditForm({ ...editForm, imageUrl: e.target.value })}
                          className="form-input"
                          style={{ marginTop: '0.4rem' }}
                        />
                      )}
                    </div>
                  </div>
                </div>

                <div className="form-group">
                  <label className="checkbox-label">
                    <input
                      type="checkbox"
                      checked={editForm.available}
                      onChange={(e) => setEditForm({ ...editForm, available: e.target.checked })}
                    />
                    <span>Còn hàng (Có thể phục vụ ngay)</span>
                  </label>
                </div>
              </div>

              <div className="modal-footer">
                <button
                  type="button"
                  className="btn-outline"
                  onClick={() => setShowEditModal(false)}
                  disabled={isSubmitting}
                >
                  Huỷ bỏ
                </button>
                <button type="submit" className="btn-primary" disabled={isSubmitting}>
                  {isSubmitting ? 'Đang lưu...' : '💾 Lưu thay đổi'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </StaffLayout>
  );
};
