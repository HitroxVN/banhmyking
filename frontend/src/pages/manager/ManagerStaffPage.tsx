import { useEffect, useState } from 'react';
import { UsersRound } from 'lucide-react';
import { managerApi } from '../../api/managerApi';
import { Badge, EmptyState, PageHeader, Skeleton } from '../../components/ui';
import type { UserInfoResponse } from '../../types/auth';
import '../../styles/components/table.css';

const ROLE_LABEL: Record<string, string> = { STAFF: 'Nhân viên', SHIPPER: 'Tài xế', MANAGER: 'Quản lý' };

/** MANAGER — nhân sự của cơ sở mình (chỉ xem; ADMIN thêm/sửa ở trang Tài khoản) */
export const ManagerStaffPage = () => {
  const [people, setPeople] = useState<UserInfoResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    managerApi
      .staff()
      .then((data) => {
        if (alive) setPeople(data);
      })
      .catch((err) => {
        if (alive) setError(err instanceof Error ? err.message : 'Không tải được nhân sự');
      })
      .finally(() => {
        if (alive) setIsLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  return (
    <>
      <PageHeader title="Nhân viên cơ sở" subtitle="Danh sách chỉ xem. Thêm, sửa tài khoản do quản trị viên thực hiện." />
      {isLoading ? (
        <Skeleton variant="row" />
      ) : error || people.length === 0 ? (
        <EmptyState icon={<UsersRound size={30} />} title="Chưa có nhân sự" description={error ?? 'Cơ sở chưa có nhân viên.'} />
      ) : (
        <section className="card">
          <div className="table-wrap">
            <table className="ui-table">
              <thead>
                <tr>
                  <th>Họ tên</th>
                  <th>Vai trò</th>
                  <th>Điện thoại</th>
                  <th>Email</th>
                </tr>
              </thead>
              <tbody>
                {people.map((p) => (
                  <tr key={p.id}>
                    <td>{p.fullName}</td>
                    <td>
                      <Badge tone="info">{ROLE_LABEL[p.role] ?? p.role}</Badge>
                    </td>
                    <td>{p.phone ?? '—'}</td>
                    <td>{p.email}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </>
  );
};
