import { useCallback, useEffect, useState } from 'react';
import { FolderTree, Pencil, Plus, Trash2, XCircle } from 'lucide-react';
import {
  Button,
  EmptyState,
  Input,
  Modal,
  PageHeader,
  Skeleton,
  Textarea,
  useConfirm,
  useToast,
} from '../../components/ui';
import { staffCatalogApi } from '../../api/staffCatalogApi';
import type { CategoryPayload } from '../../api/staffCatalogApi';
import type { CategoryItem } from '../../types/staff';
import '../../styles/components/admin-categories.css';

interface CategoryFormModalProps {
  /** null = thêm mới */
  category: CategoryItem | null;
  onClose: () => void;
  onSaved: () => void;
}

const CategoryFormModal = ({ category, onClose, onSaved }: CategoryFormModalProps) => {
  const [name, setName] = useState(category?.name ?? '');
  const [description, setDescription] = useState(category?.description ?? '');
  const [sortOrder, setSortOrder] = useState(String(category?.sortOrder ?? 0));
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const toast = useToast();

  const handleSubmit = async () => {
    if (!name.trim()) {
      setErrorMsg('Vui lòng nhập tên danh mục.');
      return;
    }

    const payload: CategoryPayload = {
      name: name.trim(),
      description: description.trim() || undefined,
      sortOrder: Number(sortOrder) || 0,
    };

    setIsSubmitting(true);
    setErrorMsg(null);
    try {
      if (category) {
        await staffCatalogApi.updateCategory(category.id, payload);
        toast.success(`Đã cập nhật danh mục ${payload.name}`);
      } else {
        await staffCatalogApi.createCategory(payload);
        toast.success(`Đã thêm danh mục ${payload.name}`);
      }
      onSaved();
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Lưu danh mục thất bại.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      size="sm"
      title={category ? `Sửa danh mục — ${category.name}` : 'Thêm danh mục mới'}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={isSubmitting}>
            Đóng
          </Button>
          <Button variant="primary" loading={isSubmitting} onClick={handleSubmit}>
            {category ? 'Lưu thay đổi' : 'Thêm danh mục'}
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

      <div className="acat__form">
        <Input
          label="Tên danh mục"
          required
          placeholder="Ví dụ: Bánh mì, Nước uống..."
          value={name}
          onChange={(event) => setName(event.target.value)}
        />

        <Textarea
          label="Mô tả"
          rows={3}
          placeholder="Mô tả ngắn về nhóm món này (không bắt buộc)"
          value={description}
          onChange={(event) => setDescription(event.target.value)}
        />

        <Input
          label="Thứ tự hiển thị"
          type="number"
          min={0}
          hint="Số nhỏ hiển thị trước trong thực đơn"
          value={sortOrder}
          onChange={(event) => setSortOrder(event.target.value)}
        />
      </div>
    </Modal>
  );
};

export const AdminCategoriesPage = () => {
  const [categories, setCategories] = useState<CategoryItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState<CategoryItem | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);

  const confirm = useConfirm();
  const toast = useToast();

  const load = useCallback(async () => {
    setIsLoading(true);
    setErrorMsg(null);
    try {
      const data = await staffCatalogApi.getCategories();
      setCategories([...data].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)));
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Không tải được danh sách danh mục.');
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const handleDelete = async (category: CategoryItem) => {
    const ok = await confirm({
      title: 'Xoá danh mục',
      message: `Xoá danh mục "${category.name}"? Các món thuộc danh mục này vẫn được giữ lại.`,
      confirmText: 'Xoá danh mục',
      danger: true,
    });
    if (!ok) return;

    setDeletingId(category.id);
    try {
      await staffCatalogApi.deleteCategory(category.id);
      toast.success(`Đã xoá danh mục ${category.name}`);
      await load();
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Xoá danh mục thất bại.');
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <>
      <PageHeader
        title="Danh mục món"
        subtitle={`${categories.length} danh mục đang dùng trong thực đơn`}
        actions={
          <Button
            variant="primary"
            icon={<Plus size={17} />}
            onClick={() => {
              setEditingCategory(null);
              setFormOpen(true);
            }}
          >
            Thêm danh mục
          </Button>
        }
      />

      {errorMsg && (
        <div className="alert-banner alert-error page-alert" role="alert">
          <XCircle size={18} />
          <div>{errorMsg}</div>
        </div>
      )}

      <section className="card">
        {isLoading ? (
          <div className="card__body">
            <Skeleton variant="row" count={5} />
          </div>
        ) : categories.length === 0 ? (
          <div className="card__body">
            <EmptyState
              icon={<FolderTree size={30} />}
              title="Chưa có danh mục nào"
              description="Thêm danh mục đầu tiên để nhóm các món trong thực đơn."
            />
          </div>
        ) : (
          <div className="table-wrap">
            <table className="ui-table">
              <thead>
                <tr>
                  <th>Danh mục</th>
                  <th>Mô tả</th>
                  <th>Thứ tự</th>
                  <th aria-label="Hành động" />
                </tr>
              </thead>
              <tbody>
                {categories.map((category) => (
                  <tr key={category.id}>
                    <td>
                      <span className="ui-table__primary">{category.name}</span>
                      <span className="ui-table__meta">Mã danh mục #{category.id}</span>
                    </td>
                    <td className="ui-table__clip">
                      {category.description ? (
                        <span className="acat__desc">{category.description}</span>
                      ) : (
                        <span className="ui-table__meta">Chưa có mô tả</span>
                      )}
                    </td>
                    <td className="acat__order">{category.sortOrder ?? 0}</td>
                    <td>
                      <div className="ui-table__actions">
                        <Button
                          size="sm"
                          variant="secondary"
                          icon={<Pencil size={15} />}
                          onClick={() => {
                            setEditingCategory(category);
                            setFormOpen(true);
                          }}
                        >
                          Sửa
                        </Button>
                        <Button
                          size="sm"
                          variant="ghost"
                          icon={<Trash2 size={15} />}
                          loading={deletingId === category.id}
                          onClick={() => void handleDelete(category)}
                        >
                          Xoá
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {formOpen && (
        <CategoryFormModal
          category={editingCategory}
          onClose={() => setFormOpen(false)}
          onSaved={() => {
            setFormOpen(false);
            void load();
          }}
        />
      )}
    </>
  );
};
