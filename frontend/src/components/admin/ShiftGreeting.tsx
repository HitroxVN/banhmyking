import { useAuth } from '../../context/useAuth';
import { MiniBanhMi } from '../illustrations/BanhMiArt';
import '../../styles/components/shift-greeting.css';

interface ShiftLine {
  hello: string;
  note: string;
}

/** Lời chào theo giờ trong ngày — chỉ là chữ trên giao diện, không gọi API */
const lineFor = (hour: number): ShiftLine => {
  if (hour >= 5 && hour < 11) return { hello: 'Chào buổi sáng', note: 'Lò đã nóng chưa? Mẻ bánh đầu tiên đang chờ đó.' };
  if (hour >= 11 && hour < 14) return { hello: 'Trưa rồi', note: 'Giờ cao điểm — giữ nhịp tay, đơn đang về đều.' };
  if (hour >= 14 && hour < 18) return { hello: 'Chào buổi chiều', note: 'Rảnh tay thì ngó qua kho, chuẩn bị cho giờ tan tầm nha.' };
  if (hour >= 18 && hour < 22) return { hello: 'Ca tối vui vẻ', note: 'Đơn giao muộn nhớ đóng gói giữ nóng giòn.' };
  return { hello: 'Khuya rồi', note: 'Xong việc thì nghỉ ngơi nhé, mai lò lại đỏ lửa.' };
};

/**
 * Dải chào đầu trang tổng quan (admin / manager). Lấy tên gọi là chữ cuối của họ tên
 * như cách xưng hô thường ngày; thiếu tên thì chào chung.
 */
export const ShiftGreeting = () => {
  const { user } = useAuth();
  const { hello, note } = lineFor(new Date().getHours());
  const name = user?.fullName?.trim().split(/\s+/).pop();

  return (
    <section className="shift-hi" aria-label="Lời chào đầu ca">
      <div className="shift-hi__copy">
        <p className="shift-hi__hello">
          {hello}
          {name ? `, ${name}` : ''}!
        </p>
        <p className="shift-hi__note">{note}</p>
      </div>
      <span className="shift-hi__art" aria-hidden="true">
        <MiniBanhMi size={92} />
      </span>
    </section>
  );
};
