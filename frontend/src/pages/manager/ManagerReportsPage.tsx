import { useEffect, useState } from 'react';
import { BarChart3 } from 'lucide-react';
import { managerApi } from '../../api/managerApi';
import { EmptyState, PageHeader, Skeleton } from '../../components/ui';
import { formatCurrency } from '../../utils/formatters';
import type { DashboardMetrics, TopProduct } from '../../types/admin';
import '../../styles/components/dashboard.css';
import '../../styles/components/table.css';

/** MANAGER — chỉ số và món bán chạy của cơ sở mình (30 ngày gần nhất) */
export const ManagerReportsPage = () => {
  const [metrics, setMetrics] = useState<DashboardMetrics | null>(null);
  const [top, setTop] = useState<TopProduct[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    Promise.all([managerApi.metrics(), managerApi.topProducts({ limit: 10 })])
      .then(([m, t]) => {
        if (!alive) return;
        setMetrics(m);
        setTop(t);
      })
      .catch((err) => {
        if (alive) setError(err instanceof Error ? err.message : 'Không tải được báo cáo');
      })
      .finally(() => {
        if (alive) setIsLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  if (isLoading) return <Skeleton variant="card" />;
  if (error || !metrics) {
    return <EmptyState icon={<BarChart3 size={30} />} title="Chưa có báo cáo" description={error ?? ''} />;
  }

  const tiles = [
    { label: 'Doanh thu hôm nay', value: formatCurrency(metrics.todayRevenue) },
    { label: 'Đơn hôm nay', value: String(metrics.todayOrders) },
    { label: 'Đang chờ xác nhận', value: String(metrics.pendingOrders) },
    { label: 'Đang xử lý', value: String(metrics.processingOrders) },
    { label: 'Tỉ lệ giao thành công', value: `${metrics.successRate}%` },
    { label: 'Doanh thu tích luỹ', value: formatCurrency(metrics.totalRevenue) },
  ];

  return (
    <>
      <PageHeader title="Báo cáo cơ sở" subtitle="Số liệu của riêng cơ sở bạn quản lý." />
      <div className="adash__kpis">
        {tiles.map((tile) => (
          <div key={tile.label} className="kpi">
            <span className="kpi__label">{tile.label}</span>
            <strong className="kpi__value">{tile.value}</strong>
          </div>
        ))}
      </div>
      <section className="card">
        <div className="card__head">
          <h2 className="card__title">Món bán chạy (30 ngày)</h2>
        </div>
        <div className="table-wrap">
          <table className="ui-table">
            <thead>
              <tr>
                <th>Món</th>
                <th>Số lượng</th>
                <th>Doanh thu</th>
              </tr>
            </thead>
            <tbody>
              {top.map((row) => (
                <tr key={`${row.productId}-${row.productName}`}>
                  <td>{row.productName}</td>
                  <td>{row.quantitySold}</td>
                  <td>{formatCurrency(row.revenue)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  );
};
