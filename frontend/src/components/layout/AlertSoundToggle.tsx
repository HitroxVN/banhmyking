import { useState } from 'react';
import { Bell, BellOff } from 'lucide-react';
import { isAlertSoundEnabled, playAlertSound, setAlertSoundEnabled, unlockAlertSound } from '../../utils/alertSound';

/**
 * Nút bật/tắt âm báo. Trình duyệt chặn âm thanh tới khi người dùng bấm vào trang, nên bật âm
 * phải đi qua cú bấm này (mở khoá AudioContext) và kêu thử một tiếng.
 */
export const AlertSoundToggle = () => {
  const [enabled, setEnabled] = useState(isAlertSoundEnabled);

  const toggle = async () => {
    const next = !enabled;
    setAlertSoundEnabled(next);
    setEnabled(next);
    if (next && (await unlockAlertSound())) playAlertSound();
  };

  return (
    <button
      type="button"
      className={`dash__sound${enabled ? ' dash__sound--on' : ''}`}
      onClick={() => void toggle()}
      aria-pressed={enabled}
      title={enabled ? 'Tắt âm báo đơn mới' : 'Bật âm báo đơn mới'}
    >
      {enabled ? <Bell size={15} /> : <BellOff size={15} />}
      {enabled ? 'Âm báo: bật' : 'Âm báo: tắt'}
    </button>
  );
};
