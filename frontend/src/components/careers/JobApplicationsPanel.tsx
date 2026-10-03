import { useEffect, useState } from 'react';
import { Download, FileText, Inbox, Phone } from 'lucide-react';
import { jobApi } from '../../api/jobApi';
import { Badge, Button, ChipGroup, EmptyState, Pagination, Select, Skeleton, Textarea, useToast } from '../ui';
import { StoreScopeSelect } from '../store/StoreScopeSelect';
import { APPLICATION_STATUS_LABEL, APPLICATION_STATUS_TONE } from '../../utils/contentLabels';
import { formatDateTime } from '../../utils/formatters';
import { notifyInboxChanged } from '../../utils/inboxEvents';
import type { ApplicationStatus, JobApplication } from '../../types/content';
import '../../styles/components/table.css';
import '../../styles/components/content.css';

const PAGE_SIZE = 20;
const STATUSES: ApplicationStatus[] = ['NEW', 'CONTACTED', 'HIRED', 'REJECTED'];

type StatusFilter = ApplicationStatus | 'ALL';

const STATUS_FILTERS: { value: StatusFilter; label: string }[] = [
  { value: 'ALL', label: 'Tất cả' },
  ...STATUSES.map((status) => ({ value: status, label: APPLICATION_STATUS_LABEL[status] })),
];

export interface JobApplicationsPanelProps {
  /** ADMIN: lọc mọi cơ sở + lấy danh sách tin từ /admin/jobs; MANAGER: backend tự khoá cơ sở mình */
  isAdmin: boolean;
}

/** Màn hồ sơ ứng tuyển dùng chung cho ADMIN (tab Hồ sơ) và MANAGER (menu Hồ sơ ứng tuyển). */
export const JobApplicationsPanel = ({ isAdmin }: JobApplicationsPanelProps) => {
  const toast = useToast();
  const [jobs, setJobs] = useState<{ id: number; title: string }[]>([]);
  const [jobId, setJobId] = useState<number | null>(null);
  const [storeId, setStoreId] = useState<number | null>(null);
  const [status, setStatus] = useState<StatusFilter>('ALL');
  const [page, setPage] = useState(1);
  const [items, setItems] = useState<JobApplication[]>([]);
  const [totalPages, setTotalPages] = useState(1);
  const [loadedKey, setLoadedKey] = useState<string | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const [selected, setSelected] = useState<JobApplication | null>(null);
  const [draftStatus, setDraftStatus] = useState<ApplicationStatus>('NEW');
  const [draftNote, setDraftNote] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    let alive = true;
    const request = isAdmin
      ? jobApi.adminList({ size: 50 }).then((page) => page.content.map((job) => ({ id: job.id, title: job.title })))
      : jobApi.listOpen().then((list) => list.map((job) => ({ id: job.id, title: job.title })));
    request
      .then((list) => {
        if (alive) setJobs(list);
      })
      .catch(() => {
        if (alive) setJobs([]);
      });
    return () => {
      alive = false;
    };
  }, [isAdmin]);

  // Tải trong effect bằng promise (không gọi setState đồng bộ); reload() tăng reloadKey để tải lại sau khi lưu/xoá
  const requestKey = `${jobId}|${storeId}|${status}|${page}|${reloadKey}`;
  const isLoading = loadedKey === null;
  // Đang tải lại do đổi bộ lọc/trang/lưu: giữ dòng cũ nhưng làm mờ
  const isFetching = loadedKey !== requestKey;

  useEffect(() => {
    let alive = true;
    jobApi
      .listApplications({
        jobId: jobId ?? undefined,
        storeId: isAdmin ? storeId ?? undefined : undefined,
        status: status === 'ALL' ? undefined : status,
        page: page - 1,
        size: PAGE_SIZE,
      })
      .then((data) => {
        if (!alive) return;
        // Trang hiện tại vừa hết dòng (xử lý mục cuối) thì lùi một trang rồi tải lại
        if (data.content.length === 0 && page > 1) {
          setPage(Math.max(1, Math.min(page - 1, data.totalPages)));
          return;
        }
        setItems(data.content);
        setTotalPages(data.totalPages);
        // Mục đang xem không còn trong danh sách đã lọc thì đóng ngăn chi tiết
        setSelected((prev) => (prev && data.content.some((item) => item.id === prev.id) ? prev : null));
        setLoadError(null);
        setLoadedKey(requestKey);
      })
      .catch((err) => {
        if (!alive) return;
        const message = err instanceof Error ? err.message : 'Không tải được hồ sơ ứng tuyển';
        toast.error(message);
        setLoadError(message);
        setLoadedKey(requestKey);
      });
    return () => {
      alive = false;
    };
  }, [jobId, storeId, status, page, isAdmin, reloadKey, requestKey, toast]);

  const reload = () => setReloadKey((key) => key + 1);

  const select = (application: JobApplication) => {
    setSelected(application);
    setDraftStatus(application.status);
    setDraftNote(application.internalNote ?? '');
  };

  const handleSave = async () => {
    if (!selected) return;
    setIsSaving(true);
    try {
      const updated = await jobApi.updateApplication(selected.id, { status: draftStatus, internalNote: draftNote });
      toast.success('Đã cập nhật hồ sơ');
      select(updated);
      notifyInboxChanged();
      reload();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Cập nhật hồ sơ thất bại');
    } finally {
      setIsSaving(false);
    }
  };

  const handleDownload = async (application: JobApplication) => {
    try {
      await jobApi.downloadCv(application);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Không tải được CV');
    }
  };

  return (
    <>
      <div className="inbox-filters">
        <Select
          label="Tin tuyển dụng"
          value={jobId == null ? '' : String(jobId)}
          onChange={(event) => {
            setJobId(event.target.value ? Number(event.target.value) : null);
            setPage(1);
          }}
        >
          <option value="">Tất cả tin</option>
          {jobs.map((job) => (
            <option key={job.id} value={job.id}>
              {job.title}
            </option>
          ))}
        </Select>
        {isAdmin && (
          <StoreScopeSelect
            value={storeId}
            onChange={(id) => {
              setStoreId(id);
              setPage(1);
            }}
          />
        )}
        <ChipGroup<StatusFilter>
          options={STATUS_FILTERS}
          value={status}
          onChange={(value) => {
            setStatus(value);
            setPage(1);
          }}
          ariaLabel="Lọc trạng thái hồ sơ"
        />
      </div>

      {isLoading ? (
        <Skeleton variant="row" count={4} />
      ) : loadError ? (
        <EmptyState
          icon={<Inbox size={30} />}
          title="Không tải được hồ sơ ứng tuyển"
          description={loadError}
          action={<Button onClick={reload}>Thử lại</Button>}
        />
      ) : items.length === 0 ? (
        <EmptyState icon={<Inbox size={30} />} title="Chưa có hồ sơ" description="Hồ sơ ứng viên nộp qua trang Tuyển dụng sẽ hiện ở đây." />
      ) : (
        <div className={`inbox${selected ? '' : ' inbox--single'}`}>
          <section className="card" aria-busy={isFetching} style={isFetching ? { opacity: 0.55, transition: 'opacity .15s' } : undefined}>
            <div className="table-wrap">
              <table className="ui-table">
                <thead>
                  <tr>
                    <th>Ứng viên</th>
                    <th>Vị trí</th>
                    <th>Cơ sở</th>
                    <th>Ngày nộp</th>
                    <th>Trạng thái</th>
                    <th>CV</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((application) => (
                    <tr
                      key={application.id}
                      className={`inbox__row${selected?.id === application.id ? ' inbox__row--active' : ''}`}
                      tabIndex={0}
                      onClick={() => select(application)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter') select(application);
                      }}
                    >
                      <td>
                        <span className="ui-table__primary">{application.fullName}</span>
                        <span className="ui-table__meta">{application.phone}</span>
                      </td>
                      <td>{application.jobTitle}</td>
                      <td>{application.storeName}</td>
                      <td>{formatDateTime(application.createdAt)}</td>
                      <td>
                        <Badge tone={APPLICATION_STATUS_TONE[application.status]}>
                          {APPLICATION_STATUS_LABEL[application.status]}
                        </Badge>
                      </td>
                      <td>{application.hasCv ? <FileText size={16} aria-label="Có CV" /> : '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={page} totalPages={totalPages} onChange={setPage} />
          </section>

          {selected && (
            <aside className="card inbox__detail">
              <div className="card__body cf-form">
                <h3 className="card__title">{selected.fullName}</h3>
                <dl>
                  <dt>Vị trí</dt>
                  <dd>{selected.jobTitle}</dd>
                  <dt>Cơ sở</dt>
                  <dd>{selected.storeName}</dd>
                  <dt>Điện thoại</dt>
                  <dd>
                    <a href={`tel:${selected.phone}`}>{selected.phone}</a>
                  </dd>
                  <dt>Email</dt>
                  <dd>{selected.email ? <a href={`mailto:${selected.email}`}>{selected.email}</a> : '—'}</dd>
                  <dt>Ngày nộp</dt>
                  <dd>{formatDateTime(selected.createdAt)}</dd>
                  <dt>Người xử lý</dt>
                  <dd>
                    {selected.handledByName
                      ? `${selected.handledByName} · ${formatDateTime(selected.handledAt)}`
                      : 'Chưa xử lý'}
                  </dd>
                </dl>
                {selected.message && <div className="inbox__message">{selected.message}</div>}
                <div className="inbox__actions">
                  {selected.hasCv && (
                    <Button variant="secondary" size="sm" icon={<Download size={16} />} onClick={() => void handleDownload(selected)}>
                      Tải CV
                    </Button>
                  )}
                  <a className="ui-btn ui-btn--ghost ui-btn--sm" href={`tel:${selected.phone}`}>
                    <Phone size={15} />
                    Gọi ứng viên
                  </a>
                </div>
                <Select label="Trạng thái" value={draftStatus} onChange={(event) => setDraftStatus(event.target.value as ApplicationStatus)}>
                  {STATUSES.map((value) => (
                    <option key={value} value={value}>
                      {APPLICATION_STATUS_LABEL[value]}
                    </option>
                  ))}
                </Select>
                <Textarea label="Ghi chú nội bộ" rows={4} maxLength={2000} value={draftNote} onChange={(event) => setDraftNote(event.target.value)} />
                <div className="inbox__actions">
                  <Button loading={isSaving} onClick={() => void handleSave()}>
                    Lưu
                  </Button>
                  <Button variant="ghost" onClick={() => setSelected(null)}>
                    Đóng
                  </Button>
                </div>
              </div>
            </aside>
          )}
        </div>
      )}
    </>
  );
};
