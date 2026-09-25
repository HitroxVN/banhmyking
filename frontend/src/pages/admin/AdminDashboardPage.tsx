import { useCallback, useEffect, useState } from 'react';
import { Banknote, ShoppingBag, Target, TrendingUp, XCircle } from 'lucide-react';
import { Button, PageHeader, Skeleton } from '../../components/ui';
import { RevenueChart } from '../../components/admin/RevenueChart';
import { OrderStatusBreakdown } from '../../components/admin/OrderStatusBreakdown';
import { UserBreakdownCard } from '../../components/admin/UserBreakdownCard';
import { adminDashboardApi } from '../../api/adminDashboardApi';
import type { DashboardMetrics, DailyRevenue, OrderStatusStat } from '../../types/admin';
import { formatCurrency } from '../../utils/formatters';
import '../../styles/components/dashboard.css';

export const AdminDashboardPage = () => {
  const [metrics, setMetrics] = useState<DashboardMetrics | null>(null);
  const [revenueData, setRevenueData] = useState<DailyRevenue[]>([]);
  const [orderStats, setOrderStats] = useState<OrderStatusStat[]>([]);
  const [chartDays, setChartDays] = useState<number>(7);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date>(new Date());

  const handleRefresh = useCallback(async () => {
    setIsRefreshing(true);
    setErrorMsg(null);
    try {
      const [m, r, s] = await Promise.all([
        adminDashboardApi.getMetrics(),
        adminDashboardApi.getRevenueChart(chartDays),
        adminDashboardApi.getOrderStatusStats(),
      ]);
      setMetrics(m);
      setRevenueData(r);
      setOrderStats(s);
      setLastUpdated(new Date());
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Không thể tải dữ liệu thống kê từ máy chủ.');
    } finally {
      setIsRefreshing(false);
    }
  }, [chartDays]);

  useEffect(() => {
    let isMounted = true;
    Promise.all([
      adminDashboardApi.getMetrics(),
      adminDashboardApi.getRevenueChart(chartDays),
      adminDashboardApi.getOrderStatusStats(),
    ])
      .then(([m, r, s]) => {
        if (!isMounted) return;
        setMetrics(m);
        setRevenueData(r);
        setOrderStats(s);
        setLastUpdated(new Date());
        setIsLoading(false);
      })
      .catch((err: unknown) => {
        if (!isMounted) return;
        setErrorMsg(err instanceof Error ? err.message : 'Không thể tải dữ liệu thống kê từ máy chủ.');
        setIsLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [chartDays]);

  const handleDaysChange = async (days: number) => {
    setChartDays(days);
    try {
      setRevenueData(await adminDashboardApi.getRevenueChart(days));
    } catch {
      setErrorMsg('Không tải được dữ liệu biểu đồ doanh thu.');
    }
  };

  return (
    <>
      <PageHeader
        title="Bảng điều khiển tổng quan"
        subtitle={`Cập nhật lúc ${lastUpdated.toLocaleTimeString('vi-VN')}`}
        onRefresh={handleRefresh}
        isRefreshing={isRefreshing}
      />

      {errorMsg && (
        <div className="alert-banner alert-error page-alert" role="alert">
          <XCircle size={18} />
          <div>{errorMsg}</div>
          <Button size="sm" variant="secondary" onClick={handleRefresh}>
            Thử lại
          </Button>
        </div>
      )}

      {isLoading && !metrics ? (
        <div className="adash">
          <Skeleton variant="card" count={2} />
        </div>
      ) : metrics ? (
        <div className="adash">
          <section className="adash__kpis">
            <article className="kpi">
              <div className="kpi__top">
                <span className="kpi__label">Doanh thu hôm nay</span>
                <span className="kpi__icon">
                  <Banknote size={19} />
                </span>
              </div>
              <span className="kpi__value">{formatCurrency(metrics.todayRevenue)}</span>
              <div className="kpi__foot">
                <span className="kpi__hint">Doanh số phát sinh trong ngày</span>
              </div>
            </article>

            <article className="kpi">
              <div className="kpi__top">
                <span className="kpi__label">Tổng doanh thu luỹ kế</span>
                <span className="kpi__icon">
                  <TrendingUp size={19} />
                </span>
              </div>
              <span className="kpi__value">{formatCurrency(metrics.totalRevenue)}</span>
              <div className="kpi__foot">
                <span className="kpi__hint">Từ các đơn đã giao thành công</span>
              </div>
            </article>

            <article className="kpi">
              <div className="kpi__top">
                <span className="kpi__label">Đơn hàng hôm nay</span>
                <span className="kpi__icon">
                  <ShoppingBag size={19} />
                </span>
              </div>
              <span className="kpi__value">
                {metrics.todayOrders} <span className="kpi__unit">đơn</span>
              </span>
              <div className="kpi__foot">
                <span className="kpi__hint">{metrics.pendingOrders} đơn đang chờ xử lý</span>
              </div>
            </article>

            <article className="kpi">
              <div className="kpi__top">
                <span className="kpi__label">Tổng đơn &amp; tỷ lệ thành công</span>
                <span className="kpi__icon">
                  <Target size={19} />
                </span>
              </div>
              <span className="kpi__value">
                {metrics.totalOrders} <span className="kpi__unit">đơn</span>
                <span className="kpi__rate">{metrics.successRate}%</span>
              </span>
              <div className="kpi__foot">
                <span className="kpi__hint">
                  {metrics.deliveredOrders} giao xong · {metrics.cancelledOrders} đã huỷ
                </span>
              </div>
            </article>
          </section>

          <section className="adash__row">
            <RevenueChart
              data={revenueData}
              days={chartDays}
              onDaysChange={handleDaysChange}
              isLoading={isLoading}
            />
            <OrderStatusBreakdown
              stats={orderStats}
              totalOrders={metrics.totalOrders}
              successRate={metrics.successRate}
            />
          </section>

          <UserBreakdownCard metrics={metrics} />
        </div>
      ) : null}
    </>
  );
};
