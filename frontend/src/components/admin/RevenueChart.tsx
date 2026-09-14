import React, { useState } from 'react';
import type { DailyRevenue } from '../../types/admin';
import { formatCurrency } from '../../utils/formatters';

interface RevenueChartProps {
  data: DailyRevenue[];
  days: number;
  onDaysChange: (days: number) => void;
  isLoading?: boolean;
}

export const RevenueChart: React.FC<RevenueChartProps> = ({
  data,
  days,
  onDaysChange,
  isLoading = false,
}) => {
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null);

  if (isLoading) {
    return (
      <div className="chart-card">
        <div className="chart-header">
          <div>
            <h3 className="chart-title">Biểu đồ doanh thu & đơn hàng</h3>
            <p className="chart-subtitle">Đang cập nhật dữ liệu...</p>
          </div>
        </div>
        <div className="chart-loading-container">
          <div className="spinner-royal"></div>
          <p>Đang vẽ biểu đồ thống kê...</p>
        </div>
      </div>
    );
  }

  // Safe checks
  const chartData = data && data.length > 0 ? data : [];
  const maxRevenue = Math.max(...chartData.map((d) => d.revenue), 100000);
  const maxOrders = Math.max(...chartData.map((d) => d.orderCount), 5);

  // SVG dimensions
  const svgWidth = 720;
  const svgHeight = 280;
  const paddingLeft = 65;
  const paddingRight = 30;
  const paddingTop = 25;
  const paddingBottom = 45;

  const chartWidth = svgWidth - paddingLeft - paddingRight;
  const chartHeight = svgHeight - paddingTop - paddingBottom;

  const stepX = chartData.length > 1 ? chartWidth / (chartData.length - 1) : chartWidth / 2;

  // Grid steps (4 horizontal guide lines)
  const ySteps = [0, 0.25, 0.5, 0.75, 1];

  // Coordinates for revenue area/line
  const points = chartData.map((d, index) => {
    const x = paddingLeft + (chartData.length > 1 ? index * stepX : chartWidth / 2);
    const y = paddingTop + chartHeight - (d.revenue / maxRevenue) * chartHeight;
    return { x, y, data: d };
  });

  // SVG Area path
  const areaPath = points.length > 0
    ? `M ${points[0].x},${paddingTop + chartHeight} ` +
      points.map((p) => `L ${p.x},${p.y}`).join(' ') +
      ` L ${points[points.length - 1].x},${paddingTop + chartHeight} Z`
    : '';

  // SVG Line path
  const linePath = points.length > 0
    ? `M ${points[0].x},${points[0].y} ` +
      points.slice(1).map((p) => `L ${p.x},${p.y}`).join(' ')
    : '';

  const hoveredPoint = hoveredIndex !== null && points[hoveredIndex] ? points[hoveredIndex] : null;

  return (
    <div className="chart-card">
      <div className="chart-header">
        <div>
          <h3 className="chart-title">Biểu đồ doanh thu & đơn hàng thực tế</h3>
          <p className="chart-subtitle">
            Doanh thu thực nhận từ các đơn hàng thành công theo mốc thời gian
          </p>
        </div>

        <div className="chart-toggle-group">
          <button
            type="button"
            className={`chart-toggle-btn ${days === 7 ? 'active' : ''}`}
            onClick={() => onDaysChange(7)}
          >
            7 ngày gần nhất
          </button>
          <button
            type="button"
            className={`chart-toggle-btn ${days === 30 ? 'active' : ''}`}
            onClick={() => onDaysChange(30)}
          >
            30 ngày gần nhất
          </button>
        </div>
      </div>

      <div className="chart-svg-container">
        {chartData.length === 0 ? (
          <div className="chart-empty-state">
            <span>📊 Chưa có dữ liệu doanh thu trong khoảng thời gian này</span>
          </div>
        ) : (
          <svg
            viewBox={`0 0 ${svgWidth} ${svgHeight}`}
            className="chart-svg"
            preserveAspectRatio="none"
          >
            <defs>
              <linearGradient id="revenueGradient" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#f59e0b" stopOpacity="0.35" />
                <stop offset="100%" stopColor="#f59e0b" stopOpacity="0.0" />
              </linearGradient>
            </defs>

            {/* Horizontal Grid lines */}
            {ySteps.map((fraction, idx) => {
              const yVal = paddingTop + chartHeight - fraction * chartHeight;
              const labelAmount = maxRevenue * fraction;
              return (
                <g key={idx} className="chart-grid-row">
                  <line
                    x1={paddingLeft}
                    y1={yVal}
                    x2={svgWidth - paddingRight}
                    y2={yVal}
                    stroke="#e2e8f0"
                    strokeDasharray="4 4"
                  />
                  <text
                    x={paddingLeft - 8}
                    y={yVal + 4}
                    textAnchor="end"
                    className="chart-axis-label"
                  >
                    {fraction === 0 ? '0 đ' : formatCurrency(labelAmount)}
                  </text>
                </g>
              );
            })}

            {/* Area fill */}
            <path d={areaPath} fill="url(#revenueGradient)" />

            {/* Revenue Trend Line */}
            <path
              d={linePath}
              fill="none"
              stroke="#d97706"
              strokeWidth="3.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            />

            {/* Order Bars (in background) */}
            {points.map((p, idx) => {
              const barWidth = Math.max(12, Math.min(28, (chartWidth / chartData.length) * 0.45));
              const barHeight = (p.data.orderCount / maxOrders) * (chartHeight * 0.7);
              const barX = p.x - barWidth / 2;
              const barY = paddingTop + chartHeight - barHeight;

              return (
                <g key={`bar-${idx}`}>
                  <rect
                    x={barX}
                    y={barY}
                    width={barWidth}
                    height={barHeight}
                    rx="4"
                    className={`chart-order-bar ${hoveredIndex === idx ? 'hovered' : ''}`}
                  />
                </g>
              );
            })}

            {/* Data point circles and X-axis labels */}
            {points.map((p, idx) => {
              // Display label for every point if <= 7, else every few points
              const showLabel = chartData.length <= 10 || idx % Math.ceil(chartData.length / 8) === 0 || idx === chartData.length - 1;
              const displayDate = p.data.date.substring(5); // MM-DD

              return (
                <g key={`pt-${idx}`}>
                  {/* Vertical hover guide line */}
                  {hoveredIndex === idx && (
                    <line
                      x1={p.x}
                      y1={paddingTop}
                      x2={p.x}
                      y2={paddingTop + chartHeight}
                      stroke="#f59e0b"
                      strokeWidth="1.5"
                      strokeDasharray="3 3"
                    />
                  )}

                  {/* Circle dot on line */}
                  <circle
                    cx={p.x}
                    cy={p.y}
                    r={hoveredIndex === idx ? 7 : 4.5}
                    className={`chart-data-point ${hoveredIndex === idx ? 'hovered' : ''}`}
                  />

                  {/* Transparent hover hit target */}
                  <rect
                    x={p.x - stepX / 2}
                    y={paddingTop}
                    width={stepX}
                    height={chartHeight + paddingBottom}
                    fill="transparent"
                    onMouseEnter={() => setHoveredIndex(idx)}
                    onMouseLeave={() => setHoveredIndex(null)}
                    style={{ cursor: 'pointer' }}
                  />

                  {/* X Axis Label */}
                  {showLabel && (
                    <text
                      x={p.x}
                      y={svgHeight - 12}
                      textAnchor="middle"
                      className="chart-axis-label-x"
                    >
                      {displayDate}
                    </text>
                  )}
                </g>
              );
            })}
          </svg>
        )}

        {/* Floating Tooltip */}
        {hoveredPoint && (
          <div
            className="chart-tooltip"
            style={{
              left: `${(hoveredPoint.x / svgWidth) * 100}%`,
              top: `${(hoveredPoint.y / svgHeight) * 100 - 15}%`,
            }}
          >
            <div className="tooltip-date">
              📅 {hoveredPoint.data.dayOfWeek}, {hoveredPoint.data.date}
            </div>
            <div className="tooltip-revenue">
              💰 Doanh thu: <strong>{formatCurrency(hoveredPoint.data.revenue)}</strong>
            </div>
            <div className="tooltip-orders">
              📦 Số đơn hàng: <strong>{hoveredPoint.data.orderCount} đơn</strong>
            </div>
          </div>
        )}
      </div>

      {/* Chart Legend */}
      <div className="chart-legend-row">
        <div className="legend-item">
          <span className="legend-badge-line"></span>
          <span>Doanh thu (VND)</span>
        </div>
        <div className="legend-item">
          <span className="legend-badge-bar"></span>
          <span>Số đơn thành công (Đơn)</span>
        </div>
      </div>
    </div>
  );
};
