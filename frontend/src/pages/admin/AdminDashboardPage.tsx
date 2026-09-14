import React, { useEffect, useState, useCallback } from 'react';
import { AdminLayout } from '../../components/admin/AdminLayout';
import { RevenueChart } from '../../components/admin/RevenueChart';
import { OrderStatusBreakdown } from '../../components/admin/OrderStatusBreakdown';
import { UserBreakdownCard } from '../../components/admin/UserBreakdownCard';
import { adminDashboardApi } from '../../api/adminDashboardApi';
import type { DashboardMetrics, DailyRevenue, OrderStatusStat } from '../../types/admin';
import { formatCurrency } from '../../utils/formatters';

export const AdminDashboardPage: React.FC = () => {
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
      const error = err as Error;
      console.error('Error refreshing admin dashboard data:', error);
      setErrorMsg(error.message || 'Không thể tải dữ liệu thống kê từ máy chủ.');
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
        if (isMounted) {
          setMetrics(m);
          setRevenueData(r);
          setOrderStats(s);
          setLastUpdated(new Date());
          setIsLoading(false);
        }
      })
      .catch((err: unknown) => {
        if (isMounted) {
          const error = err as Error;
          setErrorMsg(error.message || 'Không thể tải dữ liệu thống kê từ máy chủ.');
          setIsLoading(false);
        }
      });

    return () => {
      isMounted = false;
    };
  }, [chartDays]);

  // Handle Chart Days Change
  const handleDaysChange = async (days: number) => {
    setChartDays(days);
    try {
      const data = await adminDashboardApi.getRevenueChart(days);
      setRevenueData(data);
    } catch (err) {
      console.error('Error fetching revenue chart for days:', days, err);
    }
  };

  return (
    <AdminLayout
      title="Bảng Điều Khiển Tổng Quan (Dashboard)"
      subtitle={`Cập nhật số liệu kinh doanh và vận hành lúc ${lastUpdated.toLocaleTimeString('vi-VN')}`}
      onRefresh={handleRefresh}
      isRefreshing={isRefreshing}
    >
      {errorMsg && (
        <div className="alert-banner alert-error" style={{ marginBottom: '1.5rem' }}>
          <div>⚠️ {errorMsg}</div>
          <button
            type="button"
            className="btn-outline"
            style={{ marginLeft: 'auto', padding: '0.35rem 0.75rem', fontSize: '0.8rem' }}
            onClick={handleRefresh}
          >
            Thử lại
          </button>
        </div>
      )}

      {isLoading && !metrics ? (
        <div className="dashboard-loading-skeleton">
          <div className="spinner-royal"></div>
          <p>Đang tổng hợp dữ liệu doanh thu và đơn hàng...</p>
        </div>
      ) : metrics ? (
        <div className="dashboard-grid-container">
          {/* Top KPI Cards Row */}
          <section className="kpi-metrics-grid">
            {/* Card 1: Doanh thu hôm nay */}
            <div className="kpi-card revenue-today">
              <div className="kpi-card-header">
                <span className="kpi-title">Doanh thu hôm nay</span>
                <span className="kpi-icon">💰</span>
              </div>
              <div className="kpi-value">{formatCurrency(metrics.todayRevenue)}</div>
              <div className="kpi-footer">
                <span className="kpi-badge today">Hôm nay</span>
                <span className="kpi-subtext">Doanh số phát sinh thực tế</span>
              </div>
            </div>

            {/* Card 2: Tổng doanh thu lũy kế */}
            <div className="kpi-card revenue-total">
              <div className="kpi-card-header">
                <span className="kpi-title">Tổng doanh thu lũy kế</span>
                <span className="kpi-icon">📈</span>
              </div>
              <div className="kpi-value">{formatCurrency(metrics.totalRevenue)}</div>
              <div className="kpi-footer">
                <span className="kpi-badge total">Toàn thời gian</span>
                <span className="kpi-subtext">Tổng tiền từ đơn giao thành công</span>
              </div>
            </div>

            {/* Card 3: Đơn hàng hôm nay */}
            <div className="kpi-card orders-today">
              <div className="kpi-card-header">
                <span className="kpi-title">Đơn hàng hôm nay</span>
                <span className="kpi-icon">🛒</span>
              </div>
              <div className="kpi-value">{metrics.todayOrders} <span className="kpi-unit">đơn</span></div>
              <div className="kpi-footer">
                <span className="kpi-badge orders">Hoạt động</span>
                <span className="kpi-subtext">Đơn tạo mới trong ngày</span>
              </div>
            </div>

            {/* Card 4: Tổng đơn hàng & Tỷ lệ hoàn thành */}
            <div className="kpi-card orders-total">
              <div className="kpi-card-header">
                <span className="kpi-title">Tổng đơn & Tỷ lệ thành công</span>
                <span className="kpi-icon">🎯</span>
              </div>
              <div className="kpi-value">
                {metrics.totalOrders} <span className="kpi-unit">đơn</span>
                <span className="kpi-sub-rate">({metrics.orderSuccessRate}%)</span>
              </div>
              <div className="kpi-footer">
                <span className="kpi-badge success">{metrics.deliveredOrders} giao xong</span>
                <span className="kpi-badge cancelled">{metrics.cancelledOrders} đã huỷ</span>
              </div>
            </div>
          </section>

          {/* Middle Row: Revenue Chart & Order Status Breakdown */}
          <section className="dashboard-charts-row">
            <div className="dashboard-chart-main">
              <RevenueChart
                data={revenueData}
                days={chartDays}
                onDaysChange={handleDaysChange}
                isLoading={isLoading}
              />
            </div>

            <div className="dashboard-chart-side">
              <OrderStatusBreakdown
                stats={orderStats}
                totalOrders={metrics.totalOrders}
                successRate={metrics.orderSuccessRate}
              />
            </div>
          </section>

          {/* Bottom Row: User Breakdown & Quick Actions */}
          <section className="dashboard-bottom-row">
            <UserBreakdownCard metrics={metrics} />
          </section>
        </div>
      ) : null}
    </AdminLayout>
  );
};
