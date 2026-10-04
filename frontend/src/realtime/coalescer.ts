/** Gộp nhiều lần `trigger` trong `waitMs` thành một lần chạy `fn` — tránh tải lại dồn dập khi tin đến liền nhau */
export const createCoalescer = (fn: () => void, waitMs: number) => {
  let timer: ReturnType<typeof setTimeout> | null = null;

  return {
    trigger() {
      if (timer !== null) return;
      timer = setTimeout(() => {
        timer = null;
        fn();
      }, waitMs);
    },
    cancel() {
      if (timer === null) return;
      clearTimeout(timer);
      timer = null;
    },
  };
};
