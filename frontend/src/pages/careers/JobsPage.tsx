import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Briefcase, CalendarClock, MapPin, Users, Wallet } from 'lucide-react';
import { jobApi } from '../../api/jobApi';
import { storeApi } from '../../api/storeApi';
import { Badge, EmptyState, Select, Skeleton } from '../../components/ui';
import { EMPLOYMENT_TYPE_LABEL, deadlineText, storesText } from '../../utils/contentLabels';
import type { JobSummary } from '../../types/content';
import type { PublicStore } from '../../types/store';
import '../../styles/components/card.css';
import '../../styles/components/content.css';

interface LoadState {
  storeId: number | null;
  jobs: JobSummary[];
  error: string | null;
}

/** /tuyen-dung — tin đang tuyển, lọc theo cơ sở (tin toàn chuỗi luôn hiện). */
export const JobsPage = () => {
  const [stores, setStores] = useState<PublicStore[]>([]);
  const [storeId, setStoreId] = useState<number | null>(null);
  const [state, setState] = useState<LoadState | null>(null);

  useEffect(() => {
    let alive = true;
    storeApi
      .listPublic()
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

  useEffect(() => {
    let alive = true;
    jobApi
      .listOpen(storeId)
      .then((jobs) => {
        if (alive) setState({ storeId, jobs, error: null });
      })
      .catch((err) => {
        if (alive) setState({ storeId, jobs: [], error: err instanceof Error ? err.message : 'Không tải được tin tuyển dụng' });
      });
    return () => {
      alive = false;
    };
  }, [storeId]);

  return (
    <div>
      <div className="page-bar">
        <div>
          <p className="page-bar__crumb">
            <Link to="/">Trang chủ</Link> / Tuyển dụng
          </p>
          <h1 className="page-bar__title">Tuyển dụng</h1>
        </div>
      </div>

      <div className="job-toolbar">
        <Select
          label="Cơ sở"
          value={storeId == null ? '' : String(storeId)}
          onChange={(event) => setStoreId(event.target.value ? Number(event.target.value) : null)}
        >
          <option value="">Tất cả cơ sở</option>
          {stores.map((store) => (
            <option key={store.id} value={store.id}>
              {store.name}
            </option>
          ))}
        </Select>
      </div>

      {state === null || state.storeId !== storeId ? (
        <Skeleton variant="row" count={3} />
      ) : state.error ? (
        <EmptyState icon={<Briefcase size={28} />} title="Không tải được tin tuyển dụng" description={state.error} />
      ) : state.jobs.length === 0 ? (
        <EmptyState
          icon={<Briefcase size={28} />}
          title="Hiện chưa có vị trí đang tuyển"
          description="Bạn quay lại sau nhé — cửa hàng cập nhật tin tuyển dụng thường xuyên."
        />
      ) : (
        <div className="job-list">
          {state.jobs.map((job) => (
            <Link key={job.id} to={`/tuyen-dung/${job.slug}`} className="job-card">
              <h2 className="job-card__title">{job.title}</h2>
              <div className="job-card__facts">
                <span>
                  <Badge tone="info">{EMPLOYMENT_TYPE_LABEL[job.employmentType]}</Badge>
                </span>
                <span>
                  <Wallet size={15} />
                  {job.salaryText || 'Thoả thuận'}
                </span>
                <span>
                  <MapPin size={15} />
                  {storesText(job.chainWide, job.stores)}
                </span>
                <span>
                  <CalendarClock size={15} />
                  Hạn nộp: {deadlineText(job.deadline)}
                </span>
                {job.headcount != null && (
                  <span>
                    <Users size={15} />
                    Cần {job.headcount} người
                  </span>
                )}
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
};
