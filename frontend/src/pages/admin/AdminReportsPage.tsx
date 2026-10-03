import { useEffect, useState } from 'react';
import { Download, ShoppingBag, Tag, Wallet } from 'lucide-react';
import { Button, EmptyState, Input, PageHeader, Select, Skeleton, useToast } from '../../components/ui';
import { adminReportsApi } from '../../api/adminReportsApi';
import { EmptyPlate } from '../../components/illustrations/FoodDoodles';
import { StoreScopeSelect } from '../../components/store/StoreScopeSelect';
import type { PriceSavings, ReportType, TopProduct } from '../../types/admin';
import type { StoreRevenue } from '../../types/store';
import { formatCurrency } from '../../utils/formatters';
import '../../styles/components/admin-reports.css';

/** Năm loại file CSV backend hỗ trợ — nhãn ngắn để vừa một hàng nút */
const REPORT_TYPES: { type: ReportType; label: string }[] = [
  { type: 'TOP_PRODUCTS', label: 'Món bán chạy' },
  { type: 'REVENUE_BY_DAY', label: 'Doanh thu theo ngày' },
  { type: 'REVENUE_BY_CATEGORY', label: 'Theo danh mục' },
  { type: 'REVENUE_BY_SHIPPER', label: 'Theo tài xế' },
  { type: 'REVENUE_BY_STORE', label: 'Doanh thu theo cơ sở' },
];

const LIMIT_OPTIONS = [10, 20, 50];

/** Độ dài vạch so với giá trị lớn nhất (tối thiểu 2% để vạch nhỏ vẫn thấy) */
const barWidth = (value: number, max: number) => `${max > 0 ? Math.max(2, (value / max) * 100) : 0}%`;

export const AdminReportsPage = () => {
  const [rows, setRows] = useState<TopProduct[]>([]);
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [limit, setLimit] = useState(10);
  const [storeId, setStoreId] = useState<number | null>(null);
  const [storeRows, setStoreRows] = useState<StoreRevenue[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isStoreLoading, setIsStoreLoading] = useState(true);
  const [reloadKey, setReloadKey] = useState(0);
  const [exportingType, setExportingType] = useState<ReportType | null>(null);
  const [savings, setSavings] = useState<PriceSavings | null>(null);
  const [savingsError, setSavingsError] = useState(false);
  const toast = useToast();

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);

    adminReportsApi
      .getTopProducts({
        fromDate: fromDate || undefined,
        toDate: toDate || undefined,
        limit,
        storeId: storeId ?? undefined,
      })
      .then((data) => {
        if (!cancelled) setRows(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setRows([]);
          toast.error(err instanceof Error ? err.message : 'Không tải được báo cáo');
        }
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });

    return () => {
      cancelled = true;
    };
    // toast là API ổn định từ context, không cần đưa vào deps
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fromDate, toDate, limit, storeId, reloadKey]);

  useEffect(() => {
    let cancelled = false;
    setIsStoreLoading(true);
    adminReportsApi
      .getRevenueByStore({ fromDate: fromDate || undefined, toDate: toDate || undefined })
      .then((data) => {
        if (!cancelled) setStoreRows(data);
      })
      .catch(() => {
        if (!cancelled) setStoreRows([]);
      })
      .finally(() => {
        if (!cancelled) setIsStoreLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [fromDate, toDate, reloadKey]);

  useEffect(() => {
    let cancelled = false;
    setSavings(null);
    setSavingsError(false);
    adminReportsApi
      .getPriceSavings({
        fromDate: fromDate || undefined,
        toDate: toDate || undefined,
        storeId: storeId ?? undefined,
      })
      .then((data) => {
        if (cancelled) return;
        setSavings(data);
        setSavingsError(false);
      })
      .catch(() => {
        if (cancelled) return;
        setSavings(null);
        setSavingsError(true);
      });
    return () => {
      cancelled = true;
    };
  }, [fromDate, toDate, storeId, reloadKey]);

  const handleExport = async (type: ReportType) => {
    setExportingType(type);
    try {
      await adminReportsApi.downloadCsv(type, {
        fromDate: fromDate || undefined,
        toDate: toDate || undefined,
        limit,
        // Báo cáo theo cơ sở luôn gộp toàn chuỗi — không lọc theo cơ sở đang chọn
        storeId: type === 'REVENUE_BY_STORE' ? undefined : storeId ?? undefined,
      });
      toast.success('Đã tải file báo cáo');
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Xuất báo cáo thất bại');
    } finally {
      setExportingType(null);
    }
  };

  const clearFilters = () => {
    setFromDate('');
    setToDate('');
    setLimit(10);
    setStoreId(null);
  };

  const totalSold = rows.reduce((sum, row) => sum + row.quantitySold, 0);
  const totalRevenue = rows.reduce((sum, row) => sum + row.revenue, 0);
  const hasFilter = Boolean(fromDate) || Boolean(toDate) || storeId != null;
  const maxSold = Math.max(0, ...rows.map((row) => row.quantitySold));
  const maxRevenue = Math.max(0, ...rows.map((row) => row.revenue));
  const maxStoreOrders = Math.max(0, ...storeRows.map((row) => row.orderCount));
  const maxStoreRevenue = Math.max(0, ...storeRows.map((row) => row.revenue));

  return (
    <div>
      <PageHeader
        title="Báo cáo bán hàng"
        subtitle="Số liệu chỉ gộp đơn đã giao thành công — đơn huỷ và giao lỗi không được tính."
        onRefresh={() => setReloadKey((key) => key + 1)}
        isRefreshing={isLoading && reloadKey > 0}
      />

      <section className="card">
        <div className="arpt__filters">
          <Input
            label="Từ ngày"
            type="date"
            value={fromDate}
            max={toDate || undefined}
            onChange={(event) => setFromDate(event.target.value)}
          />
          <Input
            label="Đến ngày"
            type="date"
            value={toDate}
            min={fromDate || undefined}
            onChange={(event) => setToDate(event.target.value)}
          />
          <Select
            label="Số dòng"
            value={limit}
            onChange={(event) => setLimit(Number(event.target.value))}
          >
            {LIMIT_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option} dòng
              </option>
            ))}
          </Select>
          <StoreScopeSelect value={storeId} onChange={setStoreId} />
          {hasFilter && (
            <Button variant="ghost" onClick={clearFilters}>
              Xoá lọc
            </Button>
          )}
        </div>
      </section>

      {/* Ô số tổng: giấy kem, gáy màu mép trái như thẻ KPI ở trang tổng quan */}
      <div className="arpt__tiles">
        <div className="arpt-tile">
          <div className="arpt-tile__top">
            <span className="arpt-tile__label">Tiền ưu đãi từ giá KM và combo</span>
            <span className="arpt-tile__icon" aria-hidden="true">
              <Tag size={18} />
            </span>
          </div>
          {savings != null ? (
            <span className="arpt-tile__value">
              {formatCurrency(savings.amount)}{' '}
              <span className="arpt-tile__unit">trên {savings.orderCount} đơn</span>
            </span>
          ) : savingsError ? (
            <span className="arpt-tile__value">—</span>
          ) : (
            <Skeleton variant="row" count={1} />
          )}
          <span className="arpt-tile__hint">
            {fromDate || toDate ? 'Đơn đã giao trong khoảng ngày đang lọc' : 'Đơn đã giao trong 30 ngày gần nhất'}
          </span>
        </div>

        <div className="arpt-tile">
          <div className="arpt-tile__top">
            <span className="arpt-tile__label">Món bán chạy · số phần</span>
            <span className="arpt-tile__icon" aria-hidden="true">
              <ShoppingBag size={18} />
            </span>
          </div>
          {isLoading ? (
            <Skeleton variant="row" count={1} />
          ) : (
            <span className="arpt-tile__value">
              {totalSold} <span className="arpt-tile__unit">phần</span>
            </span>
          )}
          <span className="arpt-tile__hint">Cộng các món trong bảng Món bán chạy</span>
        </div>

        <div className="arpt-tile">
          <div className="arpt-tile__top">
            <span className="arpt-tile__label">Món bán chạy · doanh thu</span>
            <span className="arpt-tile__icon" aria-hidden="true">
              <Wallet size={18} />
            </span>
          </div>
          {isLoading ? (
            <Skeleton variant="row" count={1} />
          ) : (
            <span className="arpt-tile__value">{formatCurrency(totalRevenue)}</span>
          )}
          <span className="arpt-tile__hint">Cộng các món trong bảng Món bán chạy</span>
        </div>
      </div>

      <section className="card">
        <div className="card__head">
          <h2 className="card__title">Món bán chạy</h2>
          <span className="arpt__legend" aria-hidden="true">
            <span className="arpt__key arpt__key--sub">Số lượng</span>
            <span className="arpt__key">Doanh thu</span>
          </span>
        </div>
        <div className="card__body">
          {isLoading ? (
            <Skeleton variant="row" count={6} />
          ) : rows.length === 0 ? (
            <EmptyState
              icon={<EmptyPlate size={120} className="adm-plate" />}
              title="Chưa có dữ liệu"
              description="Không có món nào được bán trong khoảng ngày đã chọn."
            />
          ) : (
            <div className="table-wrap">
              <table className="ui-table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Món</th>
                    <th>Số lượng bán</th>
                    <th>Doanh thu</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row, index) => (
                    <tr key={`${row.productId ?? 'deleted'}-${row.productName}-${index}`}>
                      <td className="arpt__rank">{index + 1}</td>
                      <td>
                        <span className="ui-table__primary">{row.productName}</span>
                        {row.productId === null && <span className="ui-table__meta">Món đã xoá khỏi thực đơn</span>}
                      </td>
                      <td className="arpt__measure">
                        <span className="arpt__num">{row.quantitySold}</span>
                        <span className="arpt__bar" aria-hidden="true">
                          <span className="arpt__fill arpt__fill--sub" style={{ width: barWidth(row.quantitySold, maxSold) }} />
                        </span>
                      </td>
                      <td className="ui-table__amount arpt__measure">
                        <span className="arpt__num">{formatCurrency(row.revenue)}</span>
                        <span className="arpt__bar" aria-hidden="true">
                          <span className="arpt__fill" style={{ width: barWidth(row.revenue, maxRevenue) }} />
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </section>

      <section className="card">
        <div className="card__head">
          <h2 className="card__title">Xuất dữ liệu CSV</h2>
          <span className="arpt__summary">Áp dụng đúng khoảng ngày đang lọc</span>
        </div>
        <div className="card__body arpt__exports">
          {REPORT_TYPES.map(({ type, label }) => (
            <Button
              key={type}
              variant="secondary"
              size="sm"
              icon={<Download size={16} />}
              loading={exportingType === type}
              disabled={exportingType !== null}
              onClick={() => handleExport(type)}
            >
              {label}
            </Button>
          ))}
        </div>
      </section>

      {storeId == null && (
        <section className="card">
          <div className="card__head">
            <h2 className="card__title">Doanh thu theo cơ sở</h2>
          </div>
          <div className="card__body">
            {isStoreLoading ? (
              <Skeleton variant="row" count={3} />
            ) : storeRows.length === 0 ? (
              <EmptyState
                icon={<EmptyPlate size={120} className="adm-plate" />}
                title="Chưa có dữ liệu"
                description="Chưa có đơn nào được giao trong khoảng ngày đã chọn."
              />
            ) : (
              <div className="table-wrap">
                <table className="ui-table">
                  <thead>
                    <tr>
                      <th>Cơ sở</th>
                      <th>Số đơn</th>
                      <th>Doanh thu</th>
                    </tr>
                  </thead>
                  <tbody>
                    {storeRows.map((row) => (
                      <tr key={row.storeId}>
                        <td>
                          <span className="ui-table__primary">{row.storeName}</span>
                        </td>
                        <td className="arpt__measure">
                          <span className="arpt__num">{row.orderCount}</span>
                          <span className="arpt__bar" aria-hidden="true">
                            <span className="arpt__fill arpt__fill--sub" style={{ width: barWidth(row.orderCount, maxStoreOrders) }} />
                          </span>
                        </td>
                        <td className="ui-table__amount arpt__measure">
                          <span className="arpt__num">{formatCurrency(row.revenue)}</span>
                          <span className="arpt__bar" aria-hidden="true">
                            <span className="arpt__fill" style={{ width: barWidth(row.revenue, maxStoreRevenue) }} />
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </section>
      )}
    </div>
  );
};
