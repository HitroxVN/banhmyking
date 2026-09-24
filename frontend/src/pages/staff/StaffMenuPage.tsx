import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { CupSoda, ImagePlus, Plus, Sandwich, Search, Star, Upload, XCircle } from 'lucide-react';
import {
  Button,
  ChipGroup,
  EmptyState,
  Input,
  Modal,
  PageHeader,
  Select,
  Skeleton,
  Spinner,
  Textarea,
  useConfirm,
  useToast,
} from '../../components/ui';
import { staffCatalogApi } from '../../api/staffCatalogApi';
import type { CategoryItem, ProductCreatePayload, ProductItem } from '../../types/staff';
import { formatCurrency } from '../../utils/formatters';
import '../../styles/components/staff-menu.css';

const MAX_IMAGE_MB = 5;
const DEFAULT_PRICE = 35000;

const emptyForm = (categoryId: number): ProductCreatePayload => ({
  categoryId,
  name: '',
  description: '',
  imageUrl: '',
  price: DEFAULT_PRICE,
  available: true,
  featured: false,
});

export const StaffMenuPage = () => {
  const [categories, setCategories] = useState<CategoryItem[]>([]);
  const [products, setProducts] = useState<ProductItem[]>([]);
  const [selectedCategoryId, setSelectedCategoryId] = useState<number | null>(null);
  const [searchQuery, setSearchQuery] = useState('');

  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [busyProductId, setBusyProductId] = useState<number | null>(null);

  const [showCreateModal, setShowCreateModal] = useState(false);
  const [editingProduct, setEditingProduct] = useState<ProductItem | null>(null);

  const confirm = useConfirm();
  const toast = useToast();

  const load = useCallback(async (manual = false) => {
    if (manual) setIsRefreshing(true);
    setErrorMsg(null);
    try {
      const [catList, prodList] = await Promise.all([
        staffCatalogApi.getCategories(),
        // Lấy cả món đang hết hàng để bếp thấy và bật lại
        staffCatalogApi.getProducts(undefined, false),
      ]);
      setCategories(catList);
      setProducts(prodList);
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Không tải được dữ liệu thực đơn.');
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const reload = () => {
    setIsLoading(true);
    void load(true);
  };

  const filteredProducts = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();

    return products.filter((product) => {
      if (selectedCategoryId && product.categoryId !== selectedCategoryId) return false;
      if (!query) return true;
      return (
        product.name.toLowerCase().includes(query) ||
        (product.description?.toLowerCase().includes(query) ?? false)
      );
    });
  }, [products, selectedCategoryId, searchQuery]);

  const countInCategory = (categoryId: number) =>
    products.filter((product) => product.categoryId === categoryId).length;

  const replaceProduct = (updated: ProductItem) => {
    setProducts((prev) => prev.map((product) => (product.id === updated.id ? updated : product)));
  };

  const handleToggleAvailable = async (product: ProductItem) => {
    setBusyProductId(product.id);
    try {
      const updated = await staffCatalogApi.toggleProductAvailability(product);
      replaceProduct(updated);
      toast.success(`${updated.name}: ${updated.available ? 'còn hàng' : 'đã tạm hết hàng'}`);
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Cập nhật tình trạng món thất bại.');
    } finally {
      setBusyProductId(null);
    }
  };

  const handleDelete = async (product: ProductItem) => {
    const ok = await confirm({
      title: 'Xoá món khỏi thực đơn',
      message: `Xoá món "${product.name}"? Món sẽ không còn hiển thị cho khách đặt hàng.`,
      confirmText: 'Xoá món',
      danger: true,
    });
    if (!ok) return;

    try {
      await staffCatalogApi.deleteProduct(product.id);
      setProducts((prev) => prev.filter((item) => item.id !== product.id));
      toast.success(`Đã xoá món ${product.name}`);
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Xoá món ăn thất bại.');
    }
  };

  return (
    <>
      <PageHeader
        title="Thực đơn của quán"
        subtitle={`${products.length} món · sửa giá, mô tả và tình trạng còn hàng`}
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
        <div className="smenu__controls">
          <ChipGroup
            ariaLabel="Lọc theo danh mục"
            value={selectedCategoryId}
            onChange={setSelectedCategoryId}
            options={[
              { value: null, label: `Tất cả (${products.length})` },
              ...categories.map((category) => ({
                value: category.id as number | null,
                label: `${category.name} (${countInCategory(category.id)})`,
              })),
            ]}
          />

          <div className="smenu__actions">
            <Input
              type="search"
              icon={<Search size={16} />}
              placeholder="Tìm món theo tên..."
              value={searchQuery}
              onChange={(event) => setSearchQuery(event.target.value)}
            />
            <Button variant="primary" icon={<Plus size={17} />} onClick={() => setShowCreateModal(true)}>
              Thêm món mới
            </Button>
          </div>
        </div>
      </section>

      {isLoading && products.length === 0 ? (
        <section className="card">
          <div className="card__body">
            <Skeleton variant="card" count={3} />
          </div>
        </section>
      ) : filteredProducts.length === 0 ? (
        <section className="card">
          <div className="card__body">
            <EmptyState
              icon={<Sandwich size={30} />}
              title="Không tìm thấy món ăn nào"
              description='Thử đổi điều kiện tìm kiếm hoặc bấm "Thêm món mới" để tạo món cho thực đơn.'
            />
          </div>
        </section>
      ) : (
        <div className="smenu__grid">
          {filteredProducts.map((product) => {
            const isDrink = /nước|trà/.test(product.name.toLowerCase());
            const busy = busyProductId === product.id;

            return (
              <article
                key={product.id}
                className={`card smenu__card${product.available ? '' : ' smenu__card--out'}`}
              >
                <div className="smenu__media">
                  {product.imageUrl ? (
                    <img src={product.imageUrl} alt={product.name} loading="lazy" />
                  ) : (
                    <span className="smenu__placeholder" aria-hidden="true">
                      {isDrink ? <CupSoda size={40} /> : <Sandwich size={40} />}
                    </span>
                  )}
                  <span className="smenu__cat">{product.categoryName || 'Món ăn'}</span>
                  {product.featured && (
                    <span className="smenu__featured">
                      <Star size={13} /> Bán chạy
                    </span>
                  )}
                </div>

                <div className="smenu__body">
                  <div className="smenu__head">
                    <h3 className="smenu__name">{product.name}</h3>
                    <span className="smenu__price">{formatCurrency(product.price)}</span>
                  </div>
                  <p className="smenu__desc">{product.description || 'Chưa có mô tả cho món này.'}</p>

                  <Button
                    size="sm"
                    variant={product.available ? 'secondary' : 'danger'}
                    loading={busy}
                    onClick={() => void handleToggleAvailable(product)}
                  >
                    {product.available ? 'Đang bán — chuyển hết hàng' : 'Tạm hết hàng — mở bán lại'}
                  </Button>
                </div>

                <footer className="smenu__foot">
                  <Button size="sm" variant="secondary" onClick={() => setEditingProduct(product)}>
                    Sửa món / giá
                  </Button>
                  <Button size="sm" variant="ghost" onClick={() => void handleDelete(product)}>
                    Xoá
                  </Button>
                </footer>
              </article>
            );
          })}
        </div>
      )}

      {showCreateModal && (
        <ProductFormModal
          title="Thêm món ăn mới"
          categories={categories}
          initial={emptyForm(categories[0]?.id ?? 1)}
          submitLabel="Thêm vào thực đơn"
          onClose={() => setShowCreateModal(false)}
          onSubmit={async (payload) => {
            const created = await staffCatalogApi.createProduct(payload);
            setProducts((prev) => [created, ...prev]);
            setShowCreateModal(false);
            toast.success(`Đã thêm món ${created.name} vào thực đơn`);
          }}
        />
      )}

      {editingProduct && (
        <ProductFormModal
          title={`Sửa món — ${editingProduct.name}`}
          categories={categories}
          initial={{
            categoryId: editingProduct.categoryId,
            name: editingProduct.name,
            description: editingProduct.description ?? '',
            imageUrl: editingProduct.imageUrl ?? '',
            price: editingProduct.price,
            available: editingProduct.available,
            featured: editingProduct.featured,
          }}
          submitLabel="Lưu thay đổi"
          onClose={() => setEditingProduct(null)}
          onSubmit={async (payload) => {
            const updated = await staffCatalogApi.updateProduct(editingProduct.id, payload);
            replaceProduct(updated);
            setEditingProduct(null);
            toast.success(`Đã cập nhật món ${updated.name}`);
          }}
        />
      )}
    </>
  );
};

interface ProductFormModalProps {
  title: string;
  categories: CategoryItem[];
  initial: ProductCreatePayload;
  submitLabel: string;
  onClose: () => void;
  onSubmit: (payload: ProductCreatePayload) => Promise<void>;
}

/** Form thêm/sửa món — dùng chung cho cả hai modal để đổi ảnh & validate một chỗ. */
const ProductFormModal = ({
  title,
  categories,
  initial,
  submitLabel,
  onClose,
  onSubmit,
}: ProductFormModalProps) => {
  const [form, setForm] = useState(initial);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const toast = useToast();

  const handleSubmit = async () => {
    setIsSubmitting(true);
    setErrorMsg(null);
    try {
      await onSubmit(form);
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Lưu món ăn thất bại.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      size="sm"
      title={title}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={isSubmitting}>
            Đóng
          </Button>
          <Button
            variant="primary"
            loading={isSubmitting}
            disabled={!form.name.trim() || form.price < 0}
            onClick={() => void handleSubmit()}
          >
            {submitLabel}
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

      <div className="smenu__form">
        <Select
          label="Danh mục"
          required
          value={form.categoryId}
          onChange={(event) => setForm({ ...form, categoryId: Number(event.target.value) })}
        >
          {categories.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </Select>

        <Input
          label="Tên món"
          required
          placeholder="Ví dụ: Bánh mì thập cẩm đặc biệt"
          value={form.name}
          onChange={(event) => setForm({ ...form, name: event.target.value })}
        />

        <Input
          label="Giá bán (VNĐ)"
          type="number"
          required
          min={0}
          step={1000}
          value={form.price}
          onChange={(event) => setForm({ ...form, price: Number(event.target.value) })}
        />

        <Textarea
          label="Mô tả"
          rows={3}
          placeholder="Thịt xá xíu, pate gan, dưa góp, rau thơm, sốt bơ trứng..."
          value={form.description}
          onChange={(event) => setForm({ ...form, description: event.target.value })}
        />

        <ImageField
          imageUrl={form.imageUrl ?? ''}
          onChange={(imageUrl) => setForm({ ...form, imageUrl })}
          onError={(message) => toast.error(message)}
        />

        <label className="smenu__check">
          <input
            type="checkbox"
            checked={form.available}
            onChange={(event) => setForm({ ...form, available: event.target.checked })}
          />
          <span>Còn hàng (có thể phục vụ ngay)</span>
        </label>
      </div>
    </Modal>
  );
};

interface ImageFieldProps {
  imageUrl: string;
  onChange: (imageUrl: string) => void;
  onError: (message: string) => void;
}

/** Chọn ảnh món: kéo thả / chọn tệp / dán URL thủ công. */
const ImageField = ({ imageUrl, onChange, onError }: ImageFieldProps) => {
  const [isUploading, setIsUploading] = useState(false);
  const [isDragOver, setIsDragOver] = useState(false);
  const [showManualUrl, setShowManualUrl] = useState(false);
  const [imgFailed, setImgFailed] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const upload = async (file?: File) => {
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      onError('Tệp tải lên phải là ảnh (PNG, JPG, WEBP, GIF).');
      return;
    }
    if (file.size > MAX_IMAGE_MB * 1024 * 1024) {
      onError(`Dung lượng ảnh không được vượt quá ${MAX_IMAGE_MB}MB.`);
      return;
    }

    setIsUploading(true);
    try {
      onChange(await staffCatalogApi.uploadImage(file));
    } catch (err: unknown) {
      onError(err instanceof Error ? err.message : 'Tải ảnh lên thất bại.');
    } finally {
      setIsUploading(false);
    }
  };

  return (
    <div className="ui-field">
      <span className="ui-field__label">Hình ảnh món ăn</span>

      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        hidden
        disabled={isUploading}
        onChange={(event) => {
          void upload(event.target.files?.[0]);
          event.target.value = '';
        }}
      />

      {isUploading ? (
        <div className="smenu__uploading">
          <Spinner size={20} />
          <span>Đang tải ảnh lên máy chủ...</span>
        </div>
      ) : imageUrl ? (
        <div className="smenu__preview">
          {imgFailed ? (
            <span className="smenu__placeholder" aria-hidden="true">
              <Sandwich size={28} />
            </span>
          ) : (
            <img
              src={imageUrl}
              alt="Ảnh món ăn"
              className="smenu__preview-img"
              onError={() => setImgFailed(true)}
            />
          )}
          <div className="smenu__preview-actions">
            <Button size="sm" variant="secondary" onClick={() => inputRef.current?.click()}>
              Đổi ảnh khác
            </Button>
            <Button
              size="sm"
              variant="ghost"
              onClick={() => {
                setImgFailed(false);
                onChange('');
              }}
            >
              Xoá ảnh
            </Button>
          </div>
        </div>
      ) : (
        <div
          className={`smenu__dropzone${isDragOver ? ' smenu__dropzone--over' : ''}`}
          role="button"
          tabIndex={0}
          onClick={() => inputRef.current?.click()}
          onKeyDown={(event) => event.key === 'Enter' && inputRef.current?.click()}
          onDragOver={(event) => {
            event.preventDefault();
            setIsDragOver(true);
          }}
          onDragLeave={() => setIsDragOver(false)}
          onDrop={(event) => {
            event.preventDefault();
            setIsDragOver(false);
            void upload(event.dataTransfer.files?.[0]);
          }}
        >
          <ImagePlus size={22} />
          <span>Bấm hoặc kéo thả ảnh vào đây</span>
          <em>Hỗ trợ JPG, PNG, WEBP (tối đa {MAX_IMAGE_MB}MB)</em>
          <Button size="sm" variant="secondary" icon={<Upload size={15} />}>
            Chọn tệp từ máy
          </Button>
        </div>
      )}

      <button
        type="button"
        className="smenu__url-toggle"
        onClick={() => setShowManualUrl((prev) => !prev)}
      >
        {showManualUrl ? 'Ẩn nhập URL thủ công' : 'Hoặc dán URL ảnh trực tiếp'}
      </button>

      {showManualUrl && (
        <Input
          type="url"
          placeholder="https://images.unsplash.com/..."
          value={imageUrl}
          onChange={(event) => onChange(event.target.value)}
        />
      )}
    </div>
  );
};
