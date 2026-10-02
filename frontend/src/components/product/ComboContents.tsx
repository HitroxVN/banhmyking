import '../../styles/components/pricing.css';

export interface ComboContentsProps {
  items: Array<{ name: string; quantity: number }>;
  prefix?: string;
}

/** Dòng "Gồm: 1× Bánh mì · 1× Cà phê" của combo; không có thành phần thì không hiện gì. */
export const ComboContents = ({ items, prefix = 'Gồm' }: ComboContentsProps) =>
  items.length === 0 ? null : (
    <span className="combo-contents">
      {prefix}: {items.map((item) => `${item.quantity}× ${item.name}`).join(' · ')}
    </span>
  );
