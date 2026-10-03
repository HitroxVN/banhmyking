import { useEffect, useRef, useState } from 'react';
import type { ChangeEvent } from 'react';
import { ExternalLink, Newspaper, Pencil, Pin, Plus, Trash2, Upload } from 'lucide-react';
import { newsApi } from '../../api/newsApi';
import { staffCatalogApi } from '../../api/staffCatalogApi';
import {
  Badge,
  Button,
  ChipGroup,
  EmptyState,
  Input,
  Modal,
  PageHeader,
  Pagination,
  Select,
  Skeleton,
  Textarea,
  useConfirm,
  useToast,
} from '../../components/ui';
import { MarkdownEditor } from '../../components/content/MarkdownEditor';
import { NEWS_STATE_LABEL, NEWS_STATE_TONE } from '../../utils/contentLabels';
import { formatDateTime } from '../../utils/formatters';
import { toDateTimeLocal } from '../../utils/pricing';
import type { AdminNews, NewsDisplayState, NewsPayload, NewsStatus } from '../../types/content';
import '../../styles/components/table.css';
import '../../styles/components/content.css';

const PAGE_SIZE = 20;
const MAX_IMAGE_MB = 5;

type StateFilter = NewsDisplayState | 'ALL';

const FILTERS: { value: StateFilter; label: string }[] = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'DRAFT', label: 'Nháp' },
  { value: 'SCHEDULED', label: 'Hẹn giờ' },
  { value: 'PUBLISHED', label: 'Đã đăng' },
];

const EMPTY_FORM: NewsPayload = {
  title: '',
  slug: '',
  coverImageUrl: '',
  summary: '',
  content: '',
  status: 'DRAFT',
  publishedAt: null,
  pinned: false,
};

// publishedAt là LocalDateTime giờ Việt Nam: chỉ cắt chuỗi, không qua Date/toISOString nên không lệch múi giờ
const toForm = (news: AdminNews): NewsPayload => ({
  title: news.title,
  slug: news.slug,
  coverImageUrl: news.coverImageUrl ?? '',
  summary: news.summary ?? '',
  content: news.content ?? '',
  status: news.status,
  publishedAt: toDateTimeLocal(news.publishedAt) || null,
  pinned: news.pinned,
});

/** Ảnh bìa + ảnh chèn trong bài dùng chung API tải ảnh của thực đơn (ADMIN) */
const uploadImage = async (file: File): Promise<string> => {
  if (!file.type.startsWith('image/')) throw new Error('Tệp tải lên phải là ảnh (PNG, JPG, WEBP, GIF).');
  if (file.size > MAX_IMAGE_MB * 1024 * 1024) throw new Error(`Dung lượng ảnh không được vượt quá ${MAX_IMAGE_MB}MB.`);
  return staffCatalogApi.uploadImage(file);
};

/** ADMIN — soạn tin tức: lọc Nháp / Hẹn giờ / Đã đăng, form Markdown có xem trước. */
export const AdminNewsPage = () => {
  const toast = useToast();
  const confirm = useConfirm();
  const coverInputRef = useRef<HTMLInputElement>(null);
  const [filter, setFilter] = useState<StateFilter>('ALL');
  const [keyword, setKeyword] = useState('');
  const [appliedKeyword, setAppliedKeyword] = useState('');
  const [page, setPage] = useState(1);
  const [items, setItems] = useState<AdminNews[]>([]);
  const [totalPages, setTotalPages] = useState(1);
  const [loadedKey, setLoadedKey] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [editing, setEditing] = useState<{ id: number | null; form: NewsPayload } | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [isUploadingCover, setIsUploadingCover] = useState(false);

  // Tải trong effect bằng promise (không gọi setState đồng bộ); reload() tăng reloadKey để tải lại sau khi lưu/xoá
  const requestKey = `${filter}|${appliedKeyword}|${page}|${reloadKey}`;
  const isLoading = loadedKey === null;
  // Đang tải lại do đổi bộ lọc/trang/lưu: giữ dòng cũ nhưng làm mờ
  const isFetching = loadedKey !== requestKey;

  useEffect(() => {
    let alive = true;
    newsApi
      .adminList({
        status: filter === 'ALL' ? undefined : filter,
        keyword: appliedKeyword || undefined,
        page: page - 1,
        size: PAGE_SIZE,
      })
      .then((data) => {
        if (!alive) return;
        // Trang hiện tại vừa hết dòng (xoá mục cuối) thì lùi một trang rồi tải lại
        if (data.content.length === 0 && page > 1) {
          setPage(Math.max(1, Math.min(page - 1, data.totalPages)));
          return;
        }
        setItems(data.content);
        setTotalPages(data.totalPages);
        setLoadError(null);
        setLoadedKey(requestKey);
      })
      .catch((err) => {
        if (!alive) return;
        const message = err instanceof Error ? err.message : 'Không tải được danh sách bài viết';
        toast.error(message);
        setLoadError(message);
        setLoadedKey(requestKey);
      });
    return () => {
      alive = false;
    };
  }, [filter, appliedKeyword, page, reloadKey, requestKey, toast]);

  const reload = () => setReloadKey((key) => key + 1);

  // Gõ tới đâu lọc tới đó — debounce 300ms
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setAppliedKeyword(keyword.trim());
      setPage(1);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [keyword]);

  const setField = <K extends keyof NewsPayload>(key: K, value: NewsPayload[K]) =>
    setEditing((prev) => (prev ? { ...prev, form: { ...prev.form, [key]: value } } : prev));

  const openEdit = async (news: AdminNews) => {
    try {
      const full = await newsApi.adminGet(news.id);
      setEditing({ id: full.id, form: toForm(full) });
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Không tải được bài viết');
    }
  };

  const handleCover = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    setIsUploadingCover(true);
    try {
      setField('coverImageUrl', await uploadImage(file));
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Tải ảnh bìa thất bại');
    } finally {
      setIsUploadingCover(false);
    }
  };

  const handleSave = async () => {
    if (!editing) return;
    const { id, form } = editing;
    const title = form.title.trim();
    if (title.length < 3 || title.length > 200) {
      toast.error('Tiêu đề phải từ 3 đến 200 ký tự');
      return;
    }
    if (!form.content.trim()) {
      toast.error('Nội dung bài viết không được để trống');
      return;
    }
    const payload: NewsPayload = {
      ...form,
      title,
      slug: form.slug?.trim() || null,
      coverImageUrl: form.coverImageUrl?.trim() || null,
      summary: form.summary?.trim() || null,
      publishedAt: form.publishedAt || null,
    };
    setIsSaving(true);
    try {
      if (id) {
        await newsApi.adminUpdate(id, payload);
      } else {
        await newsApi.adminCreate(payload);
      }
      toast.success('Đã lưu bài viết');
      setEditing(null);
      reload();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Lưu bài viết thất bại');
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async (news: AdminNews) => {
    const accepted = await confirm({
      title: 'Xoá bài viết',
      message: `Xoá "${news.title}"? Bài sẽ biến mất khỏi trang khách.`,
      confirmText: 'Xoá',
      danger: true,
    });
    if (!accepted) return;
    try {
      await newsApi.adminDelete(news.id);
      toast.success('Đã xoá bài viết');
      reload();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Xoá bài viết thất bại');
    }
  };

  const form = editing?.form;

  return (
    <>
      <PageHeader
        title="Tin tức"
        subtitle="Bài tin tức và khuyến mãi hiển thị ở trang chủ và mục Tin tức của khách."
        actions={
          <Button icon={<Plus size={17} />} onClick={() => setEditing({ id: null, form: EMPTY_FORM })}>
            Viết bài mới
          </Button>
        }
      />

      <div className="inbox-filters">
        <ChipGroup<StateFilter>
          options={FILTERS}
          value={filter}
          onChange={(value) => {
            setFilter(value);
            setPage(1);
          }}
          ariaLabel="Lọc trạng thái bài"
        />
        <Input label="Tìm theo tiêu đề" value={keyword} onChange={(event) => setKeyword(event.target.value)} />
      </div>

      {isLoading ? (
        <Skeleton variant="row" count={4} />
      ) : loadError ? (
        <EmptyState
          icon={<Newspaper size={30} />}
          title="Không tải được danh sách bài viết"
          description={loadError}
          action={<Button onClick={reload}>Thử lại</Button>}
        />
      ) : items.length === 0 ? (
        <EmptyState icon={<Newspaper size={30} />} title="Chưa có bài viết" description="Bấm Viết bài mới để bắt đầu." />
      ) : (
        <section className="card" aria-busy={isFetching} style={isFetching ? { opacity: 0.55, transition: 'opacity .15s' } : undefined}>
          <div className="table-wrap">
            <table className="ui-table">
              <thead>
                <tr>
                  <th>Tiêu đề</th>
                  <th>Trạng thái</th>
                  <th>Thời điểm đăng</th>
                  <th>Người soạn</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {items.map((news) => (
                  <tr key={news.id}>
                    <td>
                      <span className="ui-table__primary">
                        {news.pinned && <Pin size={13} aria-label="Đang ghim" />} {news.title}
                      </span>
                      <span className="ui-table__meta">/{news.slug}</span>
                    </td>
                    <td>
                      <Badge tone={NEWS_STATE_TONE[news.displayState]}>{NEWS_STATE_LABEL[news.displayState]}</Badge>
                    </td>
                    <td>{news.publishedAt ? formatDateTime(news.publishedAt) : '—'}</td>
                    <td>{news.authorName ?? '—'}</td>
                    <td>
                      <div className="ui-table__actions">
                        {news.displayState === 'PUBLISHED' && (
                          <a className="ui-btn ui-btn--ghost ui-btn--sm" href={`/tin-tuc/${news.slug}`} target="_blank" rel="noopener noreferrer">
                            <ExternalLink size={15} />
                            Xem
                          </a>
                        )}
                        <Button size="sm" variant="ghost" icon={<Pencil size={15} />} onClick={() => void openEdit(news)}>
                          Sửa
                        </Button>
                        <Button size="sm" variant="ghost" icon={<Trash2 size={15} />} onClick={() => void handleDelete(news)}>
                          Xoá
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={page} totalPages={totalPages} onChange={setPage} />
        </section>
      )}

      <Modal
        open={editing !== null}
        onClose={() => setEditing(null)}
        size="xl"
        title={editing?.id ? 'Sửa bài viết' : 'Viết bài mới'}
        closeOnBackdrop={false}
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditing(null)}>
              Huỷ
            </Button>
            <Button loading={isSaving} onClick={() => void handleSave()}>
              Lưu bài viết
            </Button>
          </>
        }
      >
        {form && (
          <div className="cf-form">
            <Input label="Tiêu đề" required maxLength={200} value={form.title} onChange={(event) => setField('title', event.target.value)} />
            <Input
              label="Slug (đường dẫn)"
              maxLength={200}
              value={form.slug ?? ''}
              onChange={(event) => setField('slug', event.target.value)}
              hint="Bỏ trống để tự sinh từ tiêu đề (bỏ dấu). Trùng sẽ tự thêm -2, -3…"
            />
            <div className="cf-form__row">
              <Input
                label="Ảnh bìa (URL)"
                maxLength={500}
                value={form.coverImageUrl ?? ''}
                onChange={(event) => setField('coverImageUrl', event.target.value)}
              />
              <div className="ui-field">
                <span className="ui-field__label">Tải ảnh bìa</span>
                <Button variant="secondary" icon={<Upload size={16} />} loading={isUploadingCover} onClick={() => coverInputRef.current?.click()}>
                  Chọn ảnh
                </Button>
                <input ref={coverInputRef} type="file" accept="image/*" hidden onChange={(event) => void handleCover(event)} />
              </div>
            </div>
            {form.coverImageUrl && <img className="news-article__cover" src={form.coverImageUrl} alt="Ảnh bìa" />}
            <Textarea label="Tóm tắt (hiện trên thẻ bài)" rows={2} maxLength={500} value={form.summary ?? ''} onChange={(event) => setField('summary', event.target.value)} />
            <MarkdownEditor label="Nội dung" value={form.content} onChange={(value) => setField('content', value)} onUploadImage={uploadImage} />
            <div className="cf-form__row">
              <Select label="Trạng thái" value={form.status} onChange={(event) => setField('status', event.target.value as NewsStatus)}>
                <option value="DRAFT">Nháp</option>
                <option value="PUBLISHED">Đăng</option>
              </Select>
              <Input
                label="Thời điểm đăng"
                type="datetime-local"
                value={form.publishedAt ?? ''}
                onChange={(event) => setField('publishedAt', event.target.value || null)}
                hint="Giờ Việt Nam. Bỏ trống khi Đăng = đăng ngay; thời điểm tương lai = hẹn giờ."
              />
            </div>
            <label className="ui-check">
              <input type="checkbox" checked={form.pinned} onChange={(event) => setField('pinned', event.target.checked)} />
              Ghim lên đầu danh sách tin
            </label>
          </div>
        )}
      </Modal>
    </>
  );
};
