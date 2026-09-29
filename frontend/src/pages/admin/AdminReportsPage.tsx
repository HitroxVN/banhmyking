import { useEffect, useState } from 'react';
import { Download, PackageSearch } from 'lucide-react';
import { Button, EmptyState, Input, PageHeader, Select, Skeleton, useToast } from '../../components/ui';
import { adminReportsApi } from '../../api/adminReportsApi';
import type { ReportType, TopProduct } from '../../types/admin';
import { formatCurrency } from '../../utils/formatters';
import '../../styles/components/admin-reports.css';

/** Bốn loại file CSV backend hỗ trợ — nhãn ngắn để vừa một hàng nút */
const REPORT_TYPES: { type: ReportType; label: string }[] = [
  { type: 'TOP_PRODUCTS', label: 'Món bán chạy' },
  { type: 'REVENUE_BY_DAY', label: 'Doanh thu theo ngày' },
  { type: 'REVENUE_BY_CATEGORY', label: 'Theo danh mục' },
  { type: 'REVENUE_BY_SHIPPER', label: 'Theo tài xế' },
];

const LIMIT_OPTIONS = [10, 20, 50];

export const AdminReportsPage = () => {
  const [rows, setRows] = useState<TopProduct[]>([]);
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [limit, setLimit] = useState(10);
  const [isLoading, setIsLoading] = useState(true);
  const [reloadKey, setReloadKey] = useState(0);
  const [exportingType, setExportingType] = useState<ReportType | null>(null);
  const toast = useToast();

  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);

    adminReportsApi
      .getTopProducts({ fromDate: fromDate || undefined, toDate: toDate || undefined, limit })
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
  }, [fromDate, toDate, limit, reloadKey]);

  const handleExport = async (type: ReportType) => {
    setExportingType(type);
    try {
      await adminReportsApi.downloadCsv(type, {
        fromDate: fromDate || undefined,
        toDate: toDate || undefined,
        limit,
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
  };

  const totalSold = rows.reduce((sum, row) => sum + row.quantitySold, 0);
  const totalRevenue = rows.reduce((sum, row) => sum + row.revenue, 0);
  const hasFilter = Boolean(fromDate) || Boolean(toDate);

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
          {hasFilter && (
            <Button variant="ghost" onClick={clearFilters}>
              Xoá lọc
            </Button>
          )}
        </div>
      </section>

      <section className="card">
        <div className="card__head">
          <h2 className="card__title">Món bán chạy</h2>
          {!isLoading && rows.length > 0 && (
            <span className="arpt__summary">
              {totalSold} phần · {formatCurrency(totalRevenue)}
            </span>
          )}
        </div>
        <div className="card__body">
          {isLoading ? (
            <Skeleton variant="row" count={6} />
          ) : rows.length === 0 ? (
            <EmptyState
              icon={<PackageSearch size={28} />}
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
                      <td>{index + 1}</td>
                      <td>
                        <span className="ui-table__primary">{row.productName}</span>
                        {row.productId === null && <span className="ui-table__meta">Món đã xoá khỏi thực đơn</span>}
                      </td>
                      <td>{row.quantitySold}</td>
                      <td className="ui-table__amount">{formatCurrency(row.revenue)}</td>
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
    </div>
  );
};
