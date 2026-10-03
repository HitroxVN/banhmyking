import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ChevronDown,
  ChevronUp,
  CupSoda,
  ImagePlus,
  Plus,
  Search,
  Star,
  Trash2,
  Upload,
  XCircle,
} from 'lucide-react';
import {
  Badge,
  Button,
  ChipGroup,
  EmptyState,
  Input,
  Modal,
  PageHeader,
  Select,
  Skeleton,
  Spinner,
  Tabs,
  Textarea,
  useConfirm,
  useToast,
} from '../../components/ui';
import { MiniBanhMi } from '../../components/illustrations/BanhMiArt';
import { EmptyPlate } from '../../components/illustrations/FoodDoodles';
import { PriceTag } from '../../components/product/PriceTag';
import { ComboContents } from '../../components/product/ComboContents';
import { saleState, toDateTimeLocal } from '../../utils/pricing';
import { staffCatalogApi } from '../../api/staffCatalogApi';
import type {
  CategoryItem,
  ComboItemPayload,
  OptionGroupPayload,
  ProductCreatePayload,
  ProductItem,
  ProductType,
} from '../../types/staff';
import { formatCurrency } from '../../utils/formatters';
import '../../styles/components/staff-menu.css';

const MAX_IMAGE_MB = 5;
const MAX_GALLERY = 10;
const DEFAULT_PRICE = 35000;

/** Validate + tải 1 ảnh lên, dùng chung cho ảnh đại diện và bộ ảnh. */
const uploadImage = async (file: File): Promise<string> => {
  if (!file.type.startsWith('image/')) {
    throw new Error('Tệp tải lên phải là ảnh (PNG, JPG, WEBP, GIF).');
  }
  if (file.size > MAX_IMAGE_MB * 1024 * 1024) {
    throw new Error(`Dung lượng ảnh không được vượt quá ${MAX_IMAGE_MB}MB.`);
  }
  return staffCatalogApi.uploadImage(file);
};

const emptyForm = (categoryId: number): ProductCreatePayload => ({
  categoryId,
  name: '',
  description: '',
  imageUrl: '',
  images: [],
  price: DEFAULT_PRICE,
  available: true,
  featured: false,
  optionGroups: [],
  productType: 'SINGLE',
  salePrice: null,
  saleStartsAt: null,
  saleEndsAt: null,
});

const emptyCombo = (categoryId: number): ProductCreatePayload => ({
  categoryId,
  name: '',
  description: '',
  imageUrl: '',
  price: 0,
  available: true,
  featured: false,
  productType: 'COMBO',
  comboItems: [
    { productId: 0, quantity: 1 },
    { productId: 0, quantity: 1 },
  ],
});

/** Payload sửa combo từ dữ liệu đang có — giữ cờ bật/tắt của CHÍNH combo (`enabled`). */
const comboFormOf = (product: ProductItem): ProductCreatePayload => ({
  categoryId: product.categoryId,
  name: product.name,
  description: product.description ?? '',
  imageUrl: product.imageUrl ?? '',
  price: product.price,
  available: product.enabled ?? product.available,
  featured: product.featured,
  comboItems: (product.comboItems ?? []).map((item) => ({ productId: item.productId, quantity: item.quantity })),
});

/** Nhãn KM trên thẻ món (spec §7): Đang KM −x% / Sắp KM / KM đã hết. */
const SaleBadge = ({ product }: { product: ProductItem }) => {
  const state = saleState(product);
  if (state === 'ACTIVE') return <Badge tone="danger">Đang KM −{product.discountPercent ?? 0}%</Badge>;
  if (state === 'UPCOMING') return <Badge tone="info">Sắp KM</Badge>;
  if (state === 'ENDED') return <Badge tone="neutral">KM đã hết</Badge>;
  return null;
};

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
  const [tab, setTab] = useState<ProductType>('SINGLE');
  /** `product: null` = tạo combo mới */
  const [comboForm, setComboForm] = useState<{ product: ProductItem | null } | null>(null);

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
      if ((product.productType ?? 'SINGLE') !== tab) return false;
      if (selectedCategoryId && product.categoryId !== selectedCategoryId) return false;
      if (!query) return true;
      return (
        product.name.toLowerCase().includes(query) ||
        (product.description?.toLowerCase().includes(query) ?? false)
      );
    });
  }, [products, selectedCategoryId, searchQuery, tab]);

  const singles = useMemo(
    () => products.filter((product) => (product.productType ?? 'SINGLE') === 'SINGLE'),
    [products],
  );
  const comboCount = products.length - singles.length;
  // Số món theo tab đang xem (R8): danh mục và "Tất cả" chỉ đếm món cùng loại với tab
  const tabProducts = tab === 'SINGLE' ? singles : products.filter((product) => product.productType === 'COMBO');

  const countInCategory = (categoryId: number) =>
    tabProducts.filter((product) => product.categoryId === categoryId).length;

  const replaceProduct = (updated: ProductItem) => {
    setProducts((prev) => prev.map((product) => (product.id === updated.id ? updated : product)));
    // Món lẻ đổi giá/tình trạng -> combo chứa nó có thể đổi trạng thái/giá; tải lại âm thầm
    if ((updated.productType ?? 'SINGLE') === 'SINGLE' && comboCount > 0) void load();
  };

  const handleToggleAvailable = async (product: ProductItem) => {
    setBusyProductId(product.id);
    try {
      const updated = await staffCatalogApi.toggleProductAvailability(product);
      replaceProduct(updated);
      const enabled = updated.enabled ?? updated.available;
      toast.success(`${updated.name}: ${enabled ? 'bán lại toàn chuỗi' : 'đã ngừng bán toàn chuỗi'}`);
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
      if ((product.productType ?? 'SINGLE') === 'SINGLE' && comboCount > 0) void load();
      toast.success(`Đã xoá món ${product.name}`);
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Xoá món ăn thất bại.');
    }
  };

  return (
    <>
      <PageHeader
        title="Thực đơn chung"
        subtitle={`${products.length} món · sửa giá, mô tả và trạng thái bán toàn chuỗi`}
        onRefresh={reload}
        isRefreshing={isRefreshing}
      />

      {errorMsg && (
        <div className="alert-banner alert-error page-alert" role="alert">
          <XCircle size={18} />
          <div>{errorMsg}</div>
        </div>
      )}

      <Tabs<ProductType>
        tabs={[
          { key: 'SINGLE', label: 'Món lẻ', count: singles.length },
          { key: 'COMBO', label: 'Combo', count: comboCount },
        ]}
        value={tab}
        onChange={(next) => {
          setTab(next);
          setSelectedCategoryId(null);
        }}
      />

      <section className="card">
        <div className="smenu__controls">
          <ChipGroup
            ariaLabel="Lọc theo danh mục"
            value={selectedCategoryId}
            onChange={setSelectedCategoryId}
            options={[
              { value: null, label: `Tất cả (${tabProducts.length})` },
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
            <Button
              variant="primary"
              icon={<Plus size={17} />}
              onClick={() => {
                if (tab === 'COMBO') {
                  setComboForm({ product: null });
                } else {
                  setShowCreateModal(true);
                }
              }}
            >
              {tab === 'COMBO' ? 'Thêm combo' : 'Thêm món mới'}
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
          <div className="card__body smenu__empty">
            <EmptyState
              icon={<EmptyPlate size={120} />}
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
            const isCombo = product.productType === 'COMBO';
            // Nút bật/tắt dùng cờ của chính món; `available` của combo còn tính thành phần
            const isEnabled = product.enabled ?? product.available;

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
                      {isDrink ? <CupSoda size={40} /> : <MiniBanhMi size={96} />}
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
                    <PriceTag
                      className="smenu__price"
                      price={product.effectivePrice ?? product.price}
                      compareAt={product.compareAtPrice}
                    />
                  </div>
                  <div className="smenu__badges">
                    <SaleBadge product={product} />
                    {isCombo && isEnabled && !product.available && (
                      <Badge tone="warning">Tạm hết — có món thành phần đang ngừng bán</Badge>
                    )}
                  </div>
                  {isCombo && (
                    <ComboContents
                      items={(product.comboItems ?? []).map((item) => ({ name: item.name, quantity: item.quantity }))}
                    />
                  )}
                  <p className="smenu__desc">{product.description || 'Chưa có mô tả cho món này.'}</p>

                  <Button
                    size="sm"
                    variant={isEnabled ? 'secondary' : 'danger'}
                    className={`smenu__toggle smenu__toggle--${isEnabled ? 'on' : 'off'}`}
                    loading={busy}
                    onClick={() => void handleToggleAvailable(product)}
                  >
                    {isEnabled ? 'Đang bán toàn chuỗi — ngừng bán' : 'Đã ngừng bán — bán lại toàn chuỗi'}
                  </Button>
                </div>

                <footer className="smenu__foot">
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() => (isCombo ? setComboForm({ product }) : setEditingProduct(product))}
                  >
                    {isCombo ? 'Sửa combo' : 'Sửa món / giá'}
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
            images: editingProduct.images ?? [],
            price: editingProduct.price,
            available: editingProduct.enabled ?? editingProduct.available,
            salePrice: editingProduct.salePrice ?? null,
            saleStartsAt: toDateTimeLocal(editingProduct.saleStartsAt) || null,
            saleEndsAt: toDateTimeLocal(editingProduct.saleEndsAt) || null,
            featured: editingProduct.featured,
            // Gửi cả mảng = thay toàn bộ nhóm; map lại thành payload phẳng (bỏ id của row cũ).
            optionGroups: (editingProduct.optionGroups ?? []).map((group) => ({
              name: group.name,
              required: group.required,
              maxChoices: group.maxChoices,
              options: group.options.map((option) => ({ name: option.name, extraPrice: option.extraPrice })),
            })),
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

      {comboForm && (
        <ComboFormModal
          title={comboForm.product ? `Sửa combo — ${comboForm.product.name}` : 'Thêm combo mới'}
          categories={categories}
          singles={singles}
          initial={comboForm.product ? comboFormOf(comboForm.product) : emptyCombo(categories[0]?.id ?? 1)}
          submitLabel={comboForm.product ? 'Lưu combo' : 'Thêm combo'}
          onClose={() => setComboForm(null)}
          onSubmit={async (payload) => {
            const editing = comboForm.product;
            if (editing) {
              const updated = await staffCatalogApi.updateProduct(editing.id, payload);
              replaceProduct(updated);
            } else {
              const created = await staffCatalogApi.createProduct(payload);
              setProducts((prev) => [created, ...prev]);
            }
            setComboForm(null);
            toast.success(editing ? `Đã cập nhật combo ${payload.name}` : `Đã thêm combo ${payload.name}`);
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

  // Nhóm bắt buộc phải có ít nhất 1 lựa chọn (backend cũng chặn) và không được để tên trống.
  const groupsAreValid = (form.optionGroups ?? []).every(
    (group) =>
      group.name.trim() !== '' &&
      group.options.every((option) => option.name.trim() !== '') &&
      !(group.required && group.options.length === 0),
  );

  // Kiểm sớm ở client cho dễ sửa — server vẫn kiểm lại và trả lỗi tiếng Việt (spec §3)
  const saleError =
    form.salePrice == null
      ? null
      : form.salePrice <= 0
        ? 'Giá khuyến mãi phải lớn hơn 0'
        : form.salePrice >= form.price
          ? 'Giá khuyến mãi phải nhỏ hơn giá gốc'
          : form.saleStartsAt && form.saleEndsAt && form.saleEndsAt <= form.saleStartsAt
            ? 'Thời điểm kết thúc phải sau thời điểm bắt đầu'
            : null;

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
      size="md"
      title={title}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={isSubmitting}>
            Đóng
          </Button>
          <Button
            variant="primary"
            loading={isSubmitting}
            disabled={!form.name.trim() || form.price < 0 || !groupsAreValid || saleError !== null}
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
          label="Giá gốc (VNĐ)"
          type="number"
          required
          min={0}
          step={1000}
          value={form.price}
          onChange={(event) => setForm({ ...form, price: Number(event.target.value) })}
        />

        <div className="smenu__sale">
          <Input
            label="Giá khuyến mãi (VNĐ)"
            type="number"
            min={0}
            step={1000}
            placeholder="Bỏ trống = không KM"
            value={form.salePrice ?? ''}
            error={saleError ?? undefined}
            onChange={(event) =>
              event.target.value === ''
                ? setForm({ ...form, salePrice: null, saleStartsAt: null, saleEndsAt: null })
                : setForm({ ...form, salePrice: Number(event.target.value) })
            }
          />
          <Input
            label="Bắt đầu"
            type="datetime-local"
            hint="Bỏ trống = áp dụng ngay"
            disabled={form.salePrice == null}
            value={toDateTimeLocal(form.saleStartsAt)}
            onChange={(event) => setForm({ ...form, saleStartsAt: event.target.value || null })}
          />
          <Input
            label="Kết thúc"
            type="datetime-local"
            hint="Bỏ trống = không hết hạn"
            disabled={form.salePrice == null}
            value={toDateTimeLocal(form.saleEndsAt)}
            onChange={(event) => setForm({ ...form, saleEndsAt: event.target.value || null })}
          />
        </div>

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

        <GalleryField
          images={form.images ?? []}
          onChange={(images) => setForm({ ...form, images })}
          onError={(message) => toast.error(message)}
        />

        <OptionGroupsField
          groups={form.optionGroups ?? []}
          onChange={(optionGroups) => setForm({ ...form, optionGroups })}
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

const MAX_CHOICES_OPTIONS = [
  { value: 0, label: 'Không giới hạn' },
  { value: 1, label: 'Chọn 1 (radio)' },
  { value: 2, label: 'Tối đa 2' },
  { value: 3, label: 'Tối đa 3' },
];

interface OptionGroupsFieldProps {
  groups: OptionGroupPayload[];
  onChange: (groups: OptionGroupPayload[]) => void;
}

/**
 * Sửa nhóm lựa chọn của món (Size bắt buộc chọn 1, Topping chọn nhiều...).
 *
 * Bỏ hẳn một nhóm khỏi đây = xoá nhóm đó cùng lựa chọn của nó; backend trả 409 nếu lựa chọn
 * còn nằm trong giỏ khách, nên form không cần tự đoán.
 */
const OptionGroupsField = ({ groups, onChange }: OptionGroupsFieldProps) => {
  const patchGroup = (index: number, patch: Partial<OptionGroupPayload>) => {
    onChange(groups.map((group, i) => (i === index ? { ...group, ...patch } : group)));
  };

  const addGroup = () => {
    onChange([...groups, { name: '', required: false, maxChoices: 0, options: [] }]);
  };

  const removeGroup = (index: number) => {
    onChange(groups.filter((_, i) => i !== index));
  };

  const addOption = (groupIndex: number) => {
    const group = groups[groupIndex];
    patchGroup(groupIndex, { options: [...group.options, { name: '', extraPrice: 0 }] });
  };

  const patchOption = (groupIndex: number, optionIndex: number, patch: Partial<{ name: string; extraPrice: number }>) => {
    const group = groups[groupIndex];
    patchGroup(groupIndex, {
      options: group.options.map((option, i) => (i === optionIndex ? { ...option, ...patch } : option)),
    });
  };

  const removeOption = (groupIndex: number, optionIndex: number) => {
    const group = groups[groupIndex];
    patchGroup(groupIndex, { options: group.options.filter((_, i) => i !== optionIndex) });
  };

  return (
    <div className="smenu__groups">
      <div className="smenu__groups-head">
        <span className="ui-field__label">Nhóm lựa chọn (Size, Topping…)</span>
        <Button size="sm" variant="secondary" onClick={addGroup}>
          <Plus size={15} /> Thêm nhóm
        </Button>
      </div>

      {groups.length === 0 && <p className="smenu__groups-empty">Món chưa có nhóm lựa chọn nào.</p>}

      {groups.map((group, groupIndex) => (
        <div key={groupIndex} className="smenu__group">
          <div className="smenu__group-row">
            <Input
              placeholder="Tên nhóm (vd: Size, Topping)"
              value={group.name}
              onChange={(event) => patchGroup(groupIndex, { name: event.target.value })}
            />
            <Select
              value={group.maxChoices}
              onChange={(event) => patchGroup(groupIndex, { maxChoices: Number(event.target.value) })}
            >
              {MAX_CHOICES_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </Select>
            <Button size="sm" variant="ghost" onClick={() => removeGroup(groupIndex)} title="Xoá nhóm">
              <Trash2 size={15} />
            </Button>
          </div>

          <label className="smenu__check">
            <input
              type="checkbox"
              checked={group.required}
              onChange={(event) => patchGroup(groupIndex, { required: event.target.checked })}
            />
            <span>Bắt buộc khách phải chọn</span>
          </label>

          {group.options.map((option, optionIndex) => (
            <div key={optionIndex} className="smenu__group-row smenu__group-row--opt">
              <Input
                placeholder="Tên lựa chọn (vd: Lớn)"
                value={option.name}
                onChange={(event) => patchOption(groupIndex, optionIndex, { name: event.target.value })}
              />
              <Input
                type="number"
                min={0}
                placeholder="Phụ thu"
                value={option.extraPrice}
                onChange={(event) => patchOption(groupIndex, optionIndex, { extraPrice: Number(event.target.value) })}
              />
              <Button
                size="sm"
                variant="ghost"
                onClick={() => removeOption(groupIndex, optionIndex)}
                title="Xoá lựa chọn"
              >
                <Trash2 size={15} />
              </Button>
            </div>
          ))}

          <Button size="sm" variant="ghost" onClick={() => addOption(groupIndex)}>
            <Plus size={15} /> Thêm lựa chọn
          </Button>
        </div>
      ))}
    </div>
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

    setIsUploading(true);
    try {
      onChange(await uploadImage(file));
    } catch (err: unknown) {
      onError(err instanceof Error ? err.message : 'Tải ảnh lên thất bại.');
    } finally {
      setIsUploading(false);
    }
  };

  return (
    <div className="ui-field">
      <span className="ui-field__label">Ảnh đại diện (hiện ở thẻ món và lưới thực đơn)</span>

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
              <MiniBanhMi size={64} />
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

interface GalleryFieldProps {
  images: string[];
  onChange: (images: string[]) => void;
  onError: (message: string) => void;
}

/** Bộ ảnh chi tiết: thêm nhiều ảnh một lúc, đổi thứ tự, xoá. Thứ tự trong mảng = thứ tự hiển thị. */
const GalleryField = ({ images, onChange, onError }: GalleryFieldProps) => {
  const [isUploading, setIsUploading] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const addFiles = async (files: FileList | null) => {
    const picked = Array.from(files ?? []);
    if (picked.length === 0) return;
    if (images.length + picked.length > MAX_GALLERY) {
      onError(`Mỗi món chỉ được tối đa ${MAX_GALLERY} ảnh trong bộ ảnh.`);
      return;
    }

    setIsUploading(true);
    try {
      onChange([...images, ...(await Promise.all(picked.map((file) => uploadImage(file))))]);
    } catch (err: unknown) {
      onError(err instanceof Error ? err.message : 'Tải ảnh lên thất bại.');
    } finally {
      setIsUploading(false);
    }
  };

  const move = (index: number, delta: number) => {
    const target = index + delta;
    if (target < 0 || target >= images.length) return;
    const next = [...images];
    [next[index], next[target]] = [next[target], next[index]];
    onChange(next);
  };

  return (
    <div className="ui-field">
      <span className="ui-field__label">Bộ ảnh chi tiết (tuỳ chọn)</span>

      <input
        ref={inputRef}
        type="file"
        accept="image/*"
        multiple
        hidden
        disabled={isUploading}
        onChange={(event) => {
          void addFiles(event.target.files);
          event.target.value = '';
        }}
      />

      {images.length > 0 && (
        <ul className="gal">
          {images.map((url, index) => (
            <li key={url} className="gal__item">
              <img src={url} alt="" className="gal__img" />
              <div className="gal__actions">
                <button
                  type="button"
                  className="gal__btn"
                  disabled={index === 0}
                  aria-label="Đưa ảnh lên trước"
                  onClick={() => move(index, -1)}
                >
                  <ChevronUp size={14} />
                </button>
                <button
                  type="button"
                  className="gal__btn"
                  disabled={index === images.length - 1}
                  aria-label="Đưa ảnh xuống sau"
                  onClick={() => move(index, 1)}
                >
                  <ChevronDown size={14} />
                </button>
                <button
                  type="button"
                  className="gal__btn gal__btn--del"
                  aria-label="Xoá ảnh khỏi bộ ảnh"
                  onClick={() => onChange(images.filter((_, keep) => keep !== index))}
                >
                  <Trash2 size={14} />
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <Button
        size="sm"
        variant="secondary"
        icon={<ImagePlus size={15} />}
        loading={isUploading}
        disabled={images.length >= MAX_GALLERY}
        onClick={() => inputRef.current?.click()}
      >
        {images.length >= MAX_GALLERY ? `Đã đủ ${MAX_GALLERY} ảnh` : 'Thêm ảnh vào bộ ảnh'}
      </Button>

      <em className="gal__hint">
        Trong màn chi tiết món, ảnh đại diện hiện trước rồi mới tới bộ ảnh này.
      </em>
    </div>
  );
};

interface ComboFormModalProps {
  title: string;
  categories: CategoryItem[];
  /** Món lẻ chưa xoá — nguồn chọn thành phần */
  singles: ProductItem[];
  initial: ProductCreatePayload;
  submitLabel: string;
  onClose: () => void;
  onSubmit: (payload: ProductCreatePayload) => Promise<void>;
}

/**
 * Form tạo/sửa combo cố định (spec §7): bảng thành phần + dòng tóm tắt "Tổng giá lẻ X → Giá combo Y".
 * Chặn lưu ở client khi Y ≥ X hoặc thành phần sai luật; server vẫn kiểm lại.
 */
const ComboFormModal = ({
  title,
  categories,
  singles,
  initial,
  submitLabel,
  onClose,
  onSubmit,
}: ComboFormModalProps) => {
  const [form, setForm] = useState(initial);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const toast = useToast();

  const lines: ComboItemPayload[] = form.comboItems ?? [];
  const priceOf = (productId: number) => singles.find((product) => product.id === productId)?.price ?? 0;
  const originalTotal = lines.reduce((sum, line) => sum + priceOf(line.productId) * line.quantity, 0);
  const portions = lines.reduce((sum, line) => sum + line.quantity, 0);
  const savingPercent = originalTotal > 0 ? Math.floor(((originalTotal - form.price) / originalTotal) * 100) : 0;
  const hasDuplicate = new Set(lines.map((line) => line.productId)).size !== lines.length;

  const problem =
    lines.length === 0
      ? 'Thêm ít nhất một món vào combo'
      : lines.some((line) => !line.productId)
        ? 'Chọn món cho mọi dòng'
        : hasDuplicate
          ? 'Mỗi món chỉ một dòng — hãy tăng số lượng thay vì thêm dòng'
          : lines.some((line) => !Number.isInteger(line.quantity) || line.quantity < 1 || line.quantity > 20)
            ? 'Số lượng mỗi món từ 1 đến 20'
            : portions < 2
              ? 'Combo cần tổng ít nhất 2 phần món'
              : form.price <= 0 || form.price >= originalTotal
                ? 'Giá combo phải lớn hơn 0 và thấp hơn tổng giá lẻ'
                : null;

  const setLines = (comboItems: ComboItemPayload[]) => setForm({ ...form, comboItems });
  const patchLine = (index: number, patch: Partial<ComboItemPayload>) =>
    setLines(lines.map((line, i) => (i === index ? { ...line, ...patch } : line)));

  const handleSubmit = async () => {
    setIsSubmitting(true);
    setErrorMsg(null);
    try {
      await onSubmit(form);
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Lưu combo thất bại.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      size="md"
      title={title}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={isSubmitting}>
            Đóng
          </Button>
          <Button
            variant="primary"
            loading={isSubmitting}
            disabled={!form.name.trim() || problem !== null}
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
          label="Tên combo"
          required
          placeholder="Ví dụ: Combo Sáng no nê"
          value={form.name}
          onChange={(event) => setForm({ ...form, name: event.target.value })}
        />

        <Input
          label="Giá combo (VNĐ)"
          type="number"
          required
          min={0}
          step={1000}
          value={form.price}
          onChange={(event) => setForm({ ...form, price: Number(event.target.value) })}
        />

        <Textarea
          label="Mô tả"
          rows={2}
          value={form.description}
          onChange={(event) => setForm({ ...form, description: event.target.value })}
        />

        <ImageField
          imageUrl={form.imageUrl ?? ''}
          onChange={(imageUrl) => setForm({ ...form, imageUrl })}
          onError={(message) => toast.error(message)}
        />

        <div className="smenu__groups">
          <div className="smenu__groups-head">
            <span className="ui-field__label">Món trong combo</span>
            <Button size="sm" variant="secondary" onClick={() => setLines([...lines, { productId: 0, quantity: 1 }])}>
              <Plus size={15} /> Thêm món
            </Button>
          </div>

          {lines.map((line, index) => (
            <div key={index} className="smenu__group-row smenu__combo-row">
              <Select
                aria-label={`Món thứ ${index + 1}`}
                value={line.productId}
                onChange={(event) => patchLine(index, { productId: Number(event.target.value) })}
              >
                <option value={0}>— Chọn món lẻ —</option>
                {singles.map((product) => (
                  <option key={product.id} value={product.id}>
                    {product.name} · {formatCurrency(product.price)}
                  </option>
                ))}
              </Select>
              <Input
                aria-label={`Số lượng món thứ ${index + 1}`}
                type="number"
                min={1}
                max={20}
                value={line.quantity}
                onChange={(event) => patchLine(index, { quantity: Number(event.target.value) })}
              />
              <Button
                size="sm"
                variant="ghost"
                title="Xoá dòng"
                onClick={() => setLines(lines.filter((_, i) => i !== index))}
              >
                <Trash2 size={15} />
              </Button>
            </div>
          ))}

          <p className="smenu__combo-summary">
            Tổng giá lẻ {formatCurrency(originalTotal)} → Giá combo {formatCurrency(form.price)}
            {originalTotal > form.price && form.price > 0 ? ` (tiết kiệm ${savingPercent}%)` : ''}
          </p>
          {problem && (
            <p className="smenu__combo-problem" role="alert">
              {problem}
            </p>
          )}
        </div>

        <label className="smenu__check">
          <input
            type="checkbox"
            checked={form.featured ?? false}
            onChange={(event) => setForm({ ...form, featured: event.target.checked })}
          />
          <span>Nổi bật (hiện ở trang chủ)</span>
        </label>

        <label className="smenu__check">
          <input
            type="checkbox"
            checked={form.available}
            onChange={(event) => setForm({ ...form, available: event.target.checked })}
          />
          <span>Đang bán toàn chuỗi</span>
        </label>
      </div>
    </Modal>
  );
};
