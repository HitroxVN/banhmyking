import { Star } from 'lucide-react';

export interface RatingStarsProps {
  /** Điểm hiện tại, 1–5 */
  value: number;
  /** Có truyền thì thành ô chọn điểm; không truyền thì chỉ hiển thị */
  onChange?: (rating: number) => void;
  size?: number;
  /** Nhãn cho screen reader, ví dụ "4.2 trên 5" */
  label?: string;
}

const STARS = [1, 2, 3, 4, 5];

export const RatingStars = ({ value, onChange, size = 16, label }: RatingStarsProps) => {
  const rounded = Math.round(value);

  // Không có onChange = chế độ chỉ đọc: gộp thành 1 ảnh có aria-label thay vì 5 nút
  if (!onChange) {
    return (
      <span className="rstar" role="img" aria-label={label}>
        {STARS.map((star) => (
          <span key={star} className={`rstar__item${star <= rounded ? ' rstar__item--on' : ''}`}>
            <Star size={size} fill={star <= rounded ? 'currentColor' : 'none'} />
          </span>
        ))}
      </span>
    );
  }

  return (
    <span className="rstar rstar--input" role="radiogroup" aria-label={label ?? 'Chọn số sao'}>
      {STARS.map((star) => (
        <button
          key={star}
          type="button"
          className={`rstar__item rstar__item--btn${star <= rounded ? ' rstar__item--on' : ''}`}
          onClick={() => onChange(star)}
          aria-label={`${star} sao`}
          aria-pressed={star === rounded}
        >
          <Star size={size} fill={star <= rounded ? 'currentColor' : 'none'} />
        </button>
      ))}
    </span>
  );
};
