import { PageHeader } from '../../components/ui';
import { JobApplicationsPanel } from '../../components/careers/JobApplicationsPanel';

/** MANAGER — hồ sơ ứng tuyển của cơ sở mình (backend khoá phạm vi; cơ sở khác → 404). */
export const ManagerApplicationsPage = () => (
  <>
    <PageHeader title="Hồ sơ ứng tuyển" subtitle="Ứng viên muốn làm việc tại cơ sở của bạn." />
    <JobApplicationsPanel isAdmin={false} />
  </>
);
