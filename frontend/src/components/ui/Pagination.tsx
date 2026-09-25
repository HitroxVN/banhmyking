import { ChevronLeft, ChevronRight } from 'lucide-react';
import '../../styles/components/pagination.css';

export interface PaginationProps {
  /** Trang hiện tại, tính từ 1 */
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
  /** Số nút trang hiển thị quanh trang hiện tại */
  siblings?: number;
}

/** Danh sách số trang có rút gọn dạng 1 … 4 5 6 … 12 */
const buildPages = (page: number, totalPages: number, siblings: number): (number | 'gap')[] => {
  const pages: (number | 'gap')[] = [];
  const from = Math.max(2, page - siblings);
  const to = Math.min(totalPages - 1, page + siblings);

  pages.push(1);
  if (from > 2) pages.push('gap');
  for (let p = from; p <= to; p += 1) pages.push(p);
  if (to < totalPages - 1) pages.push('gap');
  if (totalPages > 1) pages.push(totalPages);

  return pages;
};

export const Pagination = ({ page, totalPages, onChange, siblings = 1 }: PaginationProps) => {
  if (totalPages <= 1) return null;

  return (
    <nav className="ui-pager" aria-label="Phân trang">
      <button
        type="button"
        className="ui-pager__btn"
        onClick={() => onChange(page - 1)}
        disabled={page <= 1}
        aria-label="Trang trước"
      >
        <ChevronLeft size={16} />
      </button>

      {buildPages(page, totalPages, siblings).map((item, index) =>
        item === 'gap' ? (
          <span key={`gap-${index}`} className="ui-pager__gap">
            …
          </span>
        ) : (
          <button
            key={item}
            type="button"
            className={`ui-pager__btn${item === page ? ' ui-pager__btn--active' : ''}`}
            onClick={() => onChange(item)}
            aria-current={item === page ? 'page' : undefined}
          >
            {item}
          </button>
        )
      )}

      <button
        type="button"
        className="ui-pager__btn"
        onClick={() => onChange(page + 1)}
        disabled={page >= totalPages}
        aria-label="Trang sau"
      >
        <ChevronRight size={16} />
      </button>
    </nav>
  );
};
