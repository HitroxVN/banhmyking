import { useEffect, useState } from 'react';
import { Lock, Pencil, Plus, Trash2, Unlock } from 'lucide-react';
import { jobApi } from '../../api/jobApi';
import { storeApi } from '../../api/storeApi';
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
  Tabs,
  useConfirm,
  useToast,
} from '../../components/ui';
import { EmptyPlate } from '../../components/illustrations/FoodDoodles';
import { MarkdownEditor } from '../../components/content/MarkdownEditor';
import { JobApplicationsPanel } from '../../components/careers/JobApplicationsPanel';
import { EMPLOYMENT_TYPE_LABEL, JOB_STATUS_LABEL, deadlineText, storesText } from '../../utils/contentLabels';
import type { AdminJob, EmploymentType, JobPayload, JobStatus, StoreRef } from '../../types/content';
import type { Store } from '../../types/store';
import '../../styles/components/table.css';
import '../../styles/components/content.css';
import '../../styles/components/admin-orders.css';

const PAGE_SIZE = 20;
const MAX_IMAGE_MB = 5;
const EMPLOYMENT_TYPES: EmploymentType[] = ['FULL_TIME', 'PART_TIME', 'SEASONAL'];

type JobsTab = 'postings' | 'applications';
type JobFilter = JobStatus | 'ALL';

const JOB_FILTERS: { value: JobFilter; label: string }[] = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'OPEN', label: 'Đang mở' },
  { value: 'CLOSED', label: 'Đã đóng' },
];

const EMPTY_JOB: JobPayload = {
  title: '',
  slug: '',
  employmentType: 'PART_TIME',
  salaryText: '',
  headcount: null,
  deadline: null,
  description: '',
  status: 'OPEN',
  storeIds: [],
};

const toJobForm = (job: AdminJob): JobPayload => ({
  title: job.title,
  slug: job.slug,
  employmentType: job.employmentType,
  salaryText: job.salaryText ?? '',
  headcount: job.headcount ?? null,
  deadline: job.deadline ?? null,
  description: job.description,
  status: job.status,
  storeIds: job.stores.map((store) => store.id),
});

const uploadImage = async (file: File): Promise<string> => {
  if (!file.type.startsWith('image/')) throw new Error('Tệp tải lên phải là ảnh (PNG, JPG, WEBP, GIF).');
  if (file.size > MAX_IMAGE_MB * 1024 * 1024) throw new Error(`Dung lượng ảnh không được vượt quá ${MAX_IMAGE_MB}MB.`);
  return staffCatalogApi.uploadImage(file);
};

/** Tab "Tin tuyển dụng": bảng + form (vị trí, hình thức, lương, số lượng, hạn nộp, cơ sở, mô tả, trạng thái). */
const JobPostingsPanel = () => {
  const toast = useToast();
  const confirm = useConfirm();
  const [stores, setStores] = useState<Store[]>([]);
  const [filter, setFilter] = useState<JobFilter>('ALL');
  const [page, setPage] = useState(1);
  const [items, setItems] = useState<AdminJob[]>([]);
  const [totalPages, setTotalPages] = useState(1);
  const [loadedKey, setLoadedKey] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  // linked: cơ sở đang gắn với tin lúc mở form, luôn hiện dù đã ngừng hoạt động hoặc không còn trong danh sách cơ sở
  const [editing, setEditing] = useState<{ id: number | null; form: JobPayload; linked: StoreRef[] } | null>(null);
  const [storeHint, setStoreHint] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    let alive = true;
    storeApi
      .adminList()
      .then((data) => {
        if (alive) setStores(data);
      })
      .catch(() => {
        if (alive) setStores([]);
      });
    return () => {
      alive = false;
    };
  }, []);

  // Tải trong effect bằng promise (không gọi setState đồng bộ); reload() tăng reloadKey để tải lại sau khi lưu/xoá
  const requestKey = `${filter}|${page}|${reloadKey}`;
  const isLoading = loadedKey === null;
  // Đang tải lại do đổi bộ lọc/trang/lưu: giữ dòng cũ nhưng làm mờ
  const isFetching = loadedKey !== requestKey;

  useEffect(() => {
    let alive = true;
    jobApi
      .adminList({ status: filter === 'ALL' ? undefined : filter, page: page - 1, size: PAGE_SIZE })
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
        const message = err instanceof Error ? err.message : 'Không tải được tin tuyển dụng';
        toast.error(message);
        setLoadError(message);
        setLoadedKey(requestKey);
      });
    return () => {
      alive = false;
    };
  }, [filter, page, reloadKey, requestKey, toast]);

  const reload = () => setReloadKey((key) => key + 1);

  const setField = <K extends keyof JobPayload>(key: K, value: JobPayload[K]) =>
    setEditing((prev) => (prev ? { ...prev, form: { ...prev.form, [key]: value } } : prev));

  const toggleStore = (storeId: number, checked: boolean) => {
    // Bỏ tích cơ sở cuối cùng sẽ thành "toàn chuỗi" một cách âm thầm: chặn, bắt tích "Toàn chuỗi" rõ ràng
    if (!checked && editing && editing.form.storeIds.length === 1 && editing.form.storeIds[0] === storeId) {
      setStoreHint('Cần giữ ít nhất một cơ sở. Muốn tuyển toàn chuỗi, hãy tích "Toàn chuỗi".');
      return;
    }
    setStoreHint(null);
    setEditing((prev) => {
      if (!prev) return prev;
      const ids = checked
        ? [...prev.form.storeIds, storeId]
        : prev.form.storeIds.filter((id) => id !== storeId);
      return { ...prev, form: { ...prev.form, storeIds: ids } };
    });
  };

  const handleSave = async () => {
    if (!editing) return;
    const { id, form } = editing;
    const title = form.title.trim();
    if (title.length < 3 || title.length > 200) {
      toast.error('Tên vị trí phải từ 3 đến 200 ký tự');
      return;
    }
    if (form.headcount != null && (form.headcount < 1 || form.headcount > 1000)) {
      toast.error('Số lượng cần tuyển phải từ 1 đến 1000');
      return;
    }
    if (!form.description.trim()) {
      toast.error('Mô tả công việc không được để trống');
      return;
    }
    const payload: JobPayload = {
      ...form,
      title,
      slug: form.slug?.trim() || null,
      salaryText: form.salaryText?.trim() || null,
      deadline: form.deadline || null,
    };
    setIsSaving(true);
    try {
      if (id) {
        await jobApi.adminUpdate(id, payload);
      } else {
        await jobApi.adminCreate(payload);
      }
      toast.success('Đã lưu tin tuyển dụng');
      setEditing(null);
      reload();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Lưu tin tuyển dụng thất bại');
    } finally {
      setIsSaving(false);
    }
  };

  const toggleStatus = async (job: AdminJob) => {
    try {
      const updated = await jobApi.adminSetStatus(job.id, job.status === 'OPEN' ? 'CLOSED' : 'OPEN');
      toast.success(updated.status === 'OPEN' ? 'Đã mở lại tin tuyển dụng' : 'Đã đóng tin tuyển dụng');
      reload();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Đổi trạng thái thất bại');
    }
  };

  const handleDelete = async (job: AdminJob) => {
    const accepted = await confirm({
      title: 'Xoá tin tuyển dụng',
      message: `Xoá "${job.title}"? Hồ sơ đã nộp vẫn được giữ lại.`,
      confirmText: 'Xoá',
      danger: true,
    });
    if (!accepted) return;
    try {
      await jobApi.adminDelete(job.id);
      toast.success('Đã xoá tin tuyển dụng');
      reload();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Xoá tin thất bại');
    }
  };

  const form = editing?.form;
  // Cơ sở đang hoạt động + cơ sở đang gắn với tin (kể cả đã ngừng hoạt động)
  const linkedIds = editing?.linked.map((store) => store.id) ?? [];
  const selectableStores: { id: number; code: string; name: string; note?: string }[] = [
    ...stores
      .filter((store) => store.active || linkedIds.includes(store.id))
      .map((store) => ({ id: store.id, code: store.code, name: store.name, note: store.active ? undefined : ' (ngừng hoạt động)' })),
    // Cơ sở đang gắn với tin nhưng không có trong danh sách cơ sở (vd. đã xoá) vẫn hiện để không bị ẩn
    ...(editing?.linked ?? [])
      .filter((ref) => !stores.some((store) => store.id === ref.id))
      .map((ref) => ({ id: ref.id, code: ref.code, name: ref.name, note: ' (không còn trong danh sách cơ sở)' })),
  ];
  const firstActiveStoreId = stores.find((store) => store.active)?.id;

  return (
    <>
      <div className="inbox-filters">
        <ChipGroup<JobFilter>
          options={JOB_FILTERS}
          value={filter}
          onChange={(value) => {
            setFilter(value);
            setPage(1);
          }}
          ariaLabel="Lọc trạng thái tin"
        />
        <Button icon={<Plus size={17} />} onClick={() => {
            setStoreHint(null);
            setEditing({ id: null, form: EMPTY_JOB, linked: [] });
          }}>
          Thêm tin tuyển dụng
        </Button>
      </div>

      {isLoading ? (
        <Skeleton variant="row" count={4} />
      ) : loadError ? (
        <section className="card">
          <div className="card__body">
            <EmptyState
              icon={<EmptyPlate size={120} className="adm-plate" />}
              title="Không tải được tin tuyển dụng"
              description={loadError}
              action={<Button onClick={reload}>Thử lại</Button>}
            />
          </div>
        </section>
      ) : items.length === 0 ? (
        <section className="card">
          <div className="card__body">
            <EmptyState icon={<EmptyPlate size={120} className="adm-plate" />} title="Chưa có tin tuyển dụng" description="Bấm Thêm tin tuyển dụng để bắt đầu." />
          </div>
        </section>
      ) : (
        <section className={`card${isFetching ? ' adm-fetching' : ''}`} aria-busy={isFetching}>
          <div className="table-wrap">
            <table className="ui-table">
              <thead>
                <tr>
                  <th>Vị trí</th>
                  <th>Hình thức</th>
                  <th>Cơ sở</th>
                  <th>Hạn nộp</th>
                  <th>Trạng thái</th>
                  <th aria-label="Hành động" />
                </tr>
              </thead>
              <tbody>
                {items.map((job) => (
                  <tr key={job.id}>
                    <td>
                      <span className="ui-table__primary">{job.title}</span>
                      <span className="ui-table__meta">/{job.slug}</span>
                    </td>
                    <td>{EMPLOYMENT_TYPE_LABEL[job.employmentType]}</td>
                    <td>{storesText(job.chainWide, job.stores)}</td>
                    <td>
                      {deadlineText(job.deadline)}
                      {job.expired && (
                        <>
                          {' '}
                          <Badge tone="danger">Hết hạn</Badge>
                        </>
                      )}
                    </td>
                    <td>
                      <Badge tone={job.status === 'OPEN' ? 'success' : 'neutral'}>{JOB_STATUS_LABEL[job.status]}</Badge>
                    </td>
                    <td>
                      <div className="ui-table__actions">
                        <Button
                          size="sm"
                          variant="ghost"
                          icon={<Pencil size={15} />}
                          onClick={() => {
                            setStoreHint(null);
                            setEditing({ id: job.id, form: toJobForm(job), linked: job.stores });
                          }}
                        >
                          Sửa
                        </Button>
                        <Button
                          size="sm"
                          variant="ghost"
                          icon={job.status === 'OPEN' ? <Lock size={15} /> : <Unlock size={15} />}
                          onClick={() => void toggleStatus(job)}
                        >
                          {job.status === 'OPEN' ? 'Đóng' : 'Mở lại'}
                        </Button>
                        <Button size="sm" variant="ghost" icon={<Trash2 size={15} />} onClick={() => void handleDelete(job)}>
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
        title={editing?.id ? 'Sửa tin tuyển dụng' : 'Thêm tin tuyển dụng'}
        closeOnBackdrop={false}
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditing(null)}>
              Huỷ
            </Button>
            <Button loading={isSaving} onClick={() => void handleSave()}>
              Lưu tin
            </Button>
          </>
        }
      >
        {form && (
          <div className="cf-form">
            <Input label="Vị trí" required maxLength={200} value={form.title} onChange={(event) => setField('title', event.target.value)} />
            <Input
              label="Slug (đường dẫn)"
              maxLength={200}
              value={form.slug ?? ''}
              onChange={(event) => setField('slug', event.target.value)}
              hint="Bỏ trống để tự sinh từ tên vị trí."
            />
            <div className="cf-form__row">
              <Select label="Hình thức" value={form.employmentType} onChange={(event) => setField('employmentType', event.target.value as EmploymentType)}>
                {EMPLOYMENT_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {EMPLOYMENT_TYPE_LABEL[type]}
                  </option>
                ))}
              </Select>
              <Input
                label="Mức lương"
                maxLength={100}
                value={form.salaryText ?? ''}
                placeholder="22–25k/giờ hoặc Thoả thuận"
                onChange={(event) => setField('salaryText', event.target.value)}
              />
            </div>
            <div className="cf-form__row">
              <Input
                label="Số lượng cần tuyển"
                type="number"
                min={1}
                max={1000}
                value={form.headcount ?? ''}
                onChange={(event) => setField('headcount', event.target.value ? Number(event.target.value) : null)}
              />
              <Input
                label="Hạn nộp hồ sơ"
                type="date"
                value={form.deadline ?? ''}
                onChange={(event) => setField('deadline', event.target.value || null)}
                hint="Bỏ trống = không hạn; hết hạn sau cuối ngày đã chọn."
              />
            </div>
            <fieldset className="ui-field">
              <legend className="ui-field__label">Cơ sở tuyển</legend>
              <label className="ui-check">
                <input
                  type="checkbox"
                  checked={form.storeIds.length === 0}
                  onChange={(event) => {
                    setStoreHint(null);
                    setField('storeIds', event.target.checked || firstActiveStoreId == null ? [] : [firstActiveStoreId]);
                  }}
                />
                Toàn chuỗi (mọi cơ sở đang hoạt động)
              </label>
              {selectableStores.map((store) => (
                <label className="ui-check" key={store.id}>
                  <input
                    type="checkbox"
                    checked={form.storeIds.includes(store.id)}
                    onChange={(event) => toggleStore(store.id, event.target.checked)}
                  />
                  {store.code} · {store.name}
                  {store.note}
                </label>
              ))}
              {storeHint && (
                <span className="ui-field__msg ui-field__msg--error" role="alert">
                  {storeHint}
                </span>
              )}
            </fieldset>
            <MarkdownEditor label="Mô tả công việc" value={form.description} onChange={(value) => setField('description', value)} onUploadImage={uploadImage} />
            <Select label="Trạng thái" value={form.status} onChange={(event) => setField('status', event.target.value as JobStatus)}>
              <option value="OPEN">Đang mở</option>
              <option value="CLOSED">Đã đóng</option>
            </Select>
          </div>
        )}
      </Modal>
    </>
  );
};

/** ADMIN — menu Tuyển dụng: tab Tin tuyển dụng + tab Hồ sơ (spec D §4). */
export const AdminJobsPage = () => {
  const [tab, setTab] = useState<JobsTab>('postings');
  return (
    <>
      <PageHeader title="Tuyển dụng" subtitle="Tin tuyển dụng theo cơ sở và hồ sơ ứng viên của toàn chuỗi." />
      <Tabs<JobsTab>
        tabs={[
          { key: 'postings', label: 'Tin tuyển dụng' },
          { key: 'applications', label: 'Hồ sơ' },
        ]}
        value={tab}
        onChange={setTab}
      />
      {tab === 'postings' ? <JobPostingsPanel /> : <JobApplicationsPanel isAdmin />}
    </>
  );
};
