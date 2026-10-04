const PREF_KEY = 'bmk_alert_sound';

let sharedContext: AudioContext | null = null;

const getContext = (): AudioContext | null => {
  if (sharedContext) return sharedContext;
  const AudioContextClass =
    window.AudioContext || (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!AudioContextClass) return null;
  sharedContext = new AudioContextClass();
  return sharedContext;
};

export const isAlertSoundEnabled = (): boolean => {
  try {
    return localStorage.getItem(PREF_KEY) !== 'off';
  } catch {
    return true;
  }
};

export const setAlertSoundEnabled = (enabled: boolean) => {
  try {
    localStorage.setItem(PREF_KEY, enabled ? 'on' : 'off');
  } catch {
    // Không lưu được (chế độ riêng tư) — lựa chọn chỉ có hiệu lực trong phiên này
  }
};

/** Gọi trong sự kiện bấm: trình duyệt chỉ cho phát âm thanh sau khi người dùng tương tác */
export const unlockAlertSound = async (): Promise<boolean> => {
  const ctx = getContext();
  if (!ctx) return false;
  try {
    await ctx.resume();
  } catch {
    // Bị chặn — trả về trạng thái hiện tại
  }
  return ctx.state === 'running';
};

const playTones = (ctx: AudioContext) => {
  const now = ctx.currentTime;
  const note = (frequency: number, start: number, end: number, volume: number) => {
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = 'sine';
    osc.frequency.setValueAtTime(frequency, now + start);
    gain.gain.setValueAtTime(volume, now + start);
    gain.gain.exponentialRampToValueAtTime(0.001, now + end);
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.start(now + start);
    osc.stop(now + end);
  };
  // "Ting" hai nốt E5 → G5
  note(659.25, 0, 0.2, 0.2);
  note(783.99, 0.12, 0.38, 0.25);
};

/** Tiếng "ting" dùng chung một AudioContext; tắt âm báo hoặc trình duyệt chặn thì im lặng */
export const playAlertSound = () => {
  if (!isAlertSoundEnabled()) return;
  const ctx = getContext();
  if (!ctx) return;
  try {
    if (ctx.state === 'running') playTones(ctx);
    else void ctx.resume().then(() => playTones(ctx)).catch(() => {});
  } catch {
    // Không bao giờ làm hỏng trang vì âm thanh
  }
};
