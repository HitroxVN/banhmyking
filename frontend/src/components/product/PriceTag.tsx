import { formatCurrency } from '../../utils/formatters';
import '../../styles/components/pricing.css';

export interface PriceTagProps {
  /** Giá đang bán */
  price: number;
  /** Giá gạch — chỉ hiện khi lớn hơn giá đang bán */
  compareAt?: number | null;
  /** Lớp cho số tiền chính, để giữ cỡ chữ riêng của từng chỗ dùng */
  className?: string;
}

/** Giá bán kèm giá gốc gạch ngang (giá KM / combo). Server quyết định có gạch hay không. */
export const PriceTag = ({ price, compareAt, className = '' }: PriceTagProps) => (
  <span className="price-tag">
    <span className={className}>{formatCurrency(price)}</span>
    {compareAt != null && compareAt > price && (
      <s className="price-tag__was">
        <span className="ui-sr-only">Giá gốc: </span>
        {formatCurrency(compareAt)}
      </s>
    )}
  </span>
);
