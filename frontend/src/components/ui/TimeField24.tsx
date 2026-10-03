import { ChevronDown } from 'lucide-react';
import '../../styles/components/field.css';

const HOURS = Array.from({ length: 24 }, (_, hour) => String(hour).padStart(2, '0'));
const MINUTE_STEP = 5;
const MINUTES = Array.from({ length: 60 / MINUTE_STEP }, (_, index) => String(index * MINUTE_STEP).padStart(2, '0'));

export interface TimeField24Props {
  label: string;
  /** Giờ dạng "HH:mm" (24h) — khớp định dạng backend nhận */
  value: string;
  onChange: (value: string) => void;
  hint?: string;
  error?: string;
}

/**
 * Chọn giờ kiểu 24h: hai ô giờ (00–23) và phút (bước 5 phút).
 * Thay cho `<input type="time">` vì trình duyệt hiện SA/CH theo ngôn ngữ máy, rất khó chỉnh.
 * Giá trị cũ có phút lẻ (vd 06:32) vẫn hiện đúng — phút đó được thêm vào danh sách.
 */
export const TimeField24 = ({ label, value, onChange, hint, error }: TimeField24Props) => {
  const [hour = '00', minute = '00'] = (value || '00:00').split(':');
  const minuteOptions = MINUTES.includes(minute) ? MINUTES : [...MINUTES, minute].sort();

  return (
    <div className="ui-field" role="group" aria-label={label}>
      <span className="ui-field__label">{label}</span>
      <span className="ui-time">
        <span className="ui-field__wrap">
          <select
            className={`ui-field__input${error ? ' ui-field__input--invalid' : ''}`}
            value={hour}
            onChange={(e) => onChange(`${e.target.value}:${minute}`)}
            aria-label={`${label} — giờ`}
          >
            {HOURS.map((h) => (
              <option key={h} value={h}>
                {h}
              </option>
            ))}
          </select>
          <span className="ui-field__caret">
            <ChevronDown size={18} />
          </span>
        </span>
        <span className="ui-time__sep" aria-hidden="true">
          :
        </span>
        <span className="ui-field__wrap">
          <select
            className={`ui-field__input${error ? ' ui-field__input--invalid' : ''}`}
            value={minute}
            onChange={(e) => onChange(`${hour}:${e.target.value}`)}
            aria-label={`${label} — phút`}
          >
            {minuteOptions.map((m) => (
              <option key={m} value={m}>
                {m}
              </option>
            ))}
          </select>
          <span className="ui-field__caret">
            <ChevronDown size={18} />
          </span>
        </span>
      </span>
      {error ? (
        <span className="ui-field__msg ui-field__msg--error">{error}</span>
      ) : hint ? (
        <span className="ui-field__msg">{hint}</span>
      ) : null}
    </div>
  );
};
