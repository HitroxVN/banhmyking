import { useState } from 'react';
import { Banknote, BarChart3, Package } from 'lucide-react';
import type { DailyRevenue } from '../../types/admin';
import { ChipGroup, EmptyState, Skeleton } from '../ui';
import { formatCurrency } from '../../utils/formatters';
import '../../styles/components/dashboard.css';

interface RevenueChartProps {
  data: DailyRevenue[];
  days: number;
  onDaysChange: (days: number) => void;
  isLoading?: boolean;
}

const DAY_OPTIONS = [
  { value: 7, label: '7 ngày gần nhất' },
  { value: 30, label: '30 ngày gần nhất' },
];

/** Biểu đồ doanh thu (đường + vùng) kèm cột số đơn thành công, vẽ thuần SVG. */
export const RevenueChart = ({ data, days, onDaysChange, isLoading = false }: RevenueChartProps) => {
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);

  const chartData = data && data.length > 0 ? data : [];
  const maxRevenue = Math.max(...chartData.map((d) => d.revenue), 100000);
  const maxOrders = Math.max(...chartData.map((d) => d.orderCount), 5);

  // Kích thước hệ toạ độ ảo — SVG tự co giãn theo bề rộng thẻ
  const svgWidth = 720;
  const svgHeight = 280;
  const paddingLeft = 65;
  const paddingRight = 30;
  const paddingTop = 25;
  const paddingBottom = 45;

  const chartWidth = svgWidth - paddingLeft - paddingRight;
  const chartHeight = svgHeight - paddingTop - paddingBottom;
  const stepX = chartData.length > 1 ? chartWidth / (chartData.length - 1) : chartWidth / 2;
  const ySteps = [0, 0.25, 0.5, 0.75, 1];

  const points = chartData.map((d, index) => {
    const x = paddingLeft + (chartData.length > 1 ? index * stepX : chartWidth / 2);
    const y = paddingTop + chartHeight - (d.revenue / maxRevenue) * chartHeight;
    return { x, y, data: d };
  });

  const baseline = paddingTop + chartHeight;
  const areaPath = points.length
    ? `M ${points[0].x},${baseline} ` +
      points.map((p) => `L ${p.x},${p.y}`).join(' ') +
      ` L ${points[points.length - 1].x},${baseline} Z`
    : '';
  const linePath = points.length
    ? `M ${points[0].x},${points[0].y} ` + points.slice(1).map((p) => `L ${p.x},${p.y}`).join(' ')
    : '';

  const hoveredPoint = hoveredIndex !== null ? points[hoveredIndex] : null;

  return (
    <section className="card">
      <div className="card__head">
        <div>
          <h3 className="chart__title">Biểu đồ doanh thu &amp; đơn hàng</h3>
          <p className="chart__sub">Doanh thu thực nhận từ các đơn hàng giao thành công</p>
        </div>
        <ChipGroup<number> options={DAY_OPTIONS} value={days} onChange={onDaysChange} ariaLabel="Khoảng thời gian" />
      </div>

      <div className="card__body">
        {isLoading && <Skeleton variant="chart" />}

        {!isLoading && chartData.length === 0 && (
          <EmptyState
            icon={<BarChart3 size={30} />}
            title="Chưa có dữ liệu doanh thu"
            description="Không có đơn giao thành công nào trong khoảng thời gian này."
          />
        )}

        {!isLoading && chartData.length > 0 && (
          <>
            <div className="chart__body">
              <svg
                viewBox={`0 0 ${svgWidth} ${svgHeight}`}
                className="chart__svg"
                preserveAspectRatio="none"
                role="img"
                aria-label={`Biểu đồ doanh thu ${days} ngày gần nhất`}
              >
                <defs>
                  <linearGradient id="revenueGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" className="chart__grad-top" />
                    <stop offset="100%" className="chart__grad-bottom" />
                  </linearGradient>
                </defs>

                {/* Lưới ngang + nhãn trục tung */}
                {ySteps.map((fraction) => {
                  const yVal = baseline - fraction * chartHeight;
                  return (
                    <g key={fraction}>
                      <line
                        className="chart__grid"
                        x1={paddingLeft}
                        y1={yVal}
                        x2={svgWidth - paddingRight}
                        y2={yVal}
                      />
                      <text className="chart__axis" x={paddingLeft - 8} y={yVal + 4} textAnchor="end">
                        {fraction === 0 ? '0 đ' : formatCurrency(maxRevenue * fraction)}
                      </text>
                    </g>
                  );
                })}

                <path d={areaPath} fill="url(#revenueGradient)" />
                <path className="chart__line" d={linePath} />

                {/* Cột số đơn thành công */}
                {points.map((p, idx) => {
                  const barWidth = Math.max(12, Math.min(28, (chartWidth / chartData.length) * 0.45));
                  const barHeight = (p.data.orderCount / maxOrders) * (chartHeight * 0.7);
                  return (
                    <rect
                      key={`bar-${p.data.date}`}
                      className={`chart__bar${hoveredIndex === idx ? ' chart__bar--on' : ''}`}
                      x={p.x - barWidth / 2}
                      y={baseline - barHeight}
                      width={barWidth}
                      height={barHeight}
                      rx="4"
                    />
                  );
                })}

                {points.map((p, idx) => {
                  const showLabel =
                    chartData.length <= 10 ||
                    idx % Math.ceil(chartData.length / 8) === 0 ||
                    idx === chartData.length - 1;

                  return (
                    <g key={`pt-${p.data.date}`}>
                      {hoveredIndex === idx && (
                        <line className="chart__guide" x1={p.x} y1={paddingTop} x2={p.x} y2={baseline} />
                      )}

                      <circle className="chart__dot" cx={p.x} cy={p.y} r={hoveredIndex === idx ? 7 : 4.5} />

                      <rect
                        className="chart__hit"
                        x={p.x - stepX / 2}
                        y={paddingTop}
                        width={stepX}
                        height={chartHeight + paddingBottom}
                        onMouseEnter={() => setHoveredIndex(idx)}
                        onMouseLeave={() => setHoveredIndex(null)}
                      />

                      {showLabel && (
                        <text className="chart__axis" x={p.x} y={svgHeight - 12} textAnchor="middle">
                          {p.data.date.substring(5)}
                        </text>
                      )}
                    </g>
                  );
                })}
              </svg>

              {hoveredPoint && (
                <div
                  className="chart__tooltip"
                  style={{
                    left: `${(hoveredPoint.x / svgWidth) * 100}%`,
                    top: `${(hoveredPoint.y / svgHeight) * 100 - 15}%`,
                  }}
                >
                  <span className="chart__tooltip-title">{hoveredPoint.data.date}</span>
                  <span className="chart__tooltip-row">
                    <Banknote size={13} /> {formatCurrency(hoveredPoint.data.revenue)}
                  </span>
                  <span className="chart__tooltip-row">
                    <Package size={13} /> {hoveredPoint.data.orderCount} đơn
                  </span>
                </div>
              )}
            </div>

            <div className="chart__legend">
              <span className="legend-item">
                <span className="legend__line" /> Doanh thu (VND)
              </span>
              <span className="legend-item">
                <span className="legend__bar" /> Số đơn thành công
              </span>
            </div>
          </>
        )}
      </div>
    </section>
  );
};
