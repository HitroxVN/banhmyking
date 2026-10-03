import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Briefcase, XCircle } from 'lucide-react';
import { jobApi } from '../../api/jobApi';
import { Button, EmptyState, Skeleton } from '../../components/ui';
import type { ApiError } from '../../api/axiosClient';
import { MarkdownView } from '../../components/content/MarkdownView';
import { ApplyForm } from '../../components/careers/ApplyForm';
import { EMPLOYMENT_TYPE_LABEL, deadlineText, storesText } from '../../utils/contentLabels';
import type { JobDetail } from '../../types/content';
import '../../styles/components/card.css';
import '../../styles/components/alert.css';
import '../../styles/components/content.css';

interface LoadState {
  slug: string;
  job: JobDetail | null;
  /** 'notfound' = 404 (tin không tồn tại/chưa đăng); 'error' = mạng/5xx → cho thử lại */
  error: 'notfound' | 'error' | null;
  message: string | null;
}

/** Hôm nay theo giờ Việt Nam, dạng yyyy-MM-dd — so sánh chuỗi với deadline */
const todayVn = (): string => new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' });

/** /tuyen-dung/:slug — mô tả Markdown + form ứng tuyển; tin đóng/hết hạn ẩn form. */
export const JobDetailPage = () => {
  const { slug = '' } = useParams();
  const [state, setState] = useState<LoadState | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let alive = true;
    jobApi
      .getBySlug(slug)
      .then((job) => {
        if (alive) setState({ slug, job, error: null, message: null });
      })
      .catch((err) => {
        if (!alive) return;
        const status = (err as ApiError | undefined)?.status;
        setState({
          slug,
          job: null,
          error: status === 404 ? 'notfound' : 'error',
          message: err instanceof Error ? err.message : null,
        });
      });
    return () => {
      alive = false;
    };
  }, [slug, reloadKey]);

  if (state === null || state.slug !== slug) {
    return <Skeleton variant="card" />;
  }

  if (!state.job) {
    if (state.error === 'error') {
      return (
        <EmptyState
          icon={<Briefcase size={28} />}
          title="Không tải được tin tuyển dụng"
          description={state.message ?? 'Đã có lỗi xảy ra, vui lòng thử lại.'}
          action={
            <Button
              onClick={() => {
                setState(null);
                setReloadKey((key) => key + 1);
              }}
            >
              Thử lại
            </Button>
          }
        />
      );
    }
    return (
      <EmptyState
        icon={<Briefcase size={28} />}
        title="Không tìm thấy tin tuyển dụng"
        description="Tin không tồn tại hoặc đã bị gỡ."
        action={
          <Link className="ui-btn ui-btn--primary ui-btn--md" to="/tuyen-dung">
            Xem các vị trí khác
          </Link>
        }
      />
    );
  }

  const job = state.job;
  return (
    <div>
      <div className="page-bar">
        <div>
          <p className="page-bar__crumb">
            <Link to="/">Trang chủ</Link> / <Link to="/tuyen-dung">Tuyển dụng</Link> / {job.title}
          </p>
          <h1 className="page-bar__title">{job.title}</h1>
        </div>
      </div>

      <div className="job-detail">
        <article className="card job-detail__desc">
          <div className="card__body">
            <MarkdownView source={job.description} />
          </div>
        </article>

        <aside className="card job-detail__aside">
          <div className="card__body">
            <dl className="job-detail__facts">
              <div>
                <dt>Hình thức</dt>
                <dd>{EMPLOYMENT_TYPE_LABEL[job.employmentType]}</dd>
              </div>
              <div>
                <dt>Mức lương</dt>
                <dd>{job.salaryText || 'Thoả thuận'}</dd>
              </div>
              {job.headcount != null && (
                <div>
                  <dt>Số lượng</dt>
                  <dd>{job.headcount} người</dd>
                </div>
              )}
              <div>
                <dt>Cơ sở nhận hồ sơ</dt>
                <dd>{storesText(job.chainWide, job.stores) || 'Chưa có cơ sở nhận hồ sơ'}</dd>
              </div>
              <div>
                <dt>Hạn nộp</dt>
                <dd>{deadlineText(job.deadline)}</dd>
              </div>
            </dl>
          </div>
        </aside>
      </div>

      <section className="card job-apply" id="apply">
        <div className="card__head">
          <h2 className="card__title">
            <span className="job-apply__kicker" aria-hidden="true">
              Vào đội nhé!
            </span>
            Ứng tuyển vị trí này
          </h2>
        </div>
        <div className="card__body">
          {job.acceptingApplications ? (
            <ApplyForm job={job} />
          ) : (
            <div className="alert-banner alert-error" role="status">
              <XCircle size={18} />
              <span>{job.deadline && job.deadline < todayVn() ? 'Đã hết hạn nhận hồ sơ' : 'Tin đã đóng'}</span>
            </div>
          )}
        </div>
      </section>
    </div>
  );
};
