/**
 * Hiệu ứng "ổ bánh bay vào giỏ" khi thêm món thành công.
 *
 * <p>Thuần trang trí: không có đích (khách chưa đăng nhập nên không có giỏ trên nav) hoặc
 * người dùng bật giảm chuyển động thì bỏ qua, không ảnh hưởng luồng thêm món.
 * Đích là phần tử có `data-cart-target` đang hiển thị (pill giỏ nổi hoặc pill trên nav).
 */

/** Ổ bánh tí hon — cùng nét với MiniBanhMi nhưng dựng bằng chuỗi vì gắn thẳng vào body */
const MINI_BANH_MI_SVG = `<svg width="46" height="21" viewBox="0 0 48 22" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M3 12 C3 18 10 20 24 20 C38 20 45 18 45 12 Z" fill="#D98A2B" stroke="#2B1D14" stroke-width="1.6"/><path d="M4 12 Q9 8 14 12 Q19 8 24 12 Q29 8 34 12 Q39 8 44 12" fill="#7DB85A" stroke="#2B1D14" stroke-width="1.4"/><path d="M1.5 11 C1.5 4.5 11 2 24 2 C37 2 46.5 4.5 46.5 11 C38 9 10 9 1.5 11 Z" fill="#E8A33D" stroke="#2B1D14" stroke-width="1.6"/></svg>`;

const prefersReducedMotion = () =>
  typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

/** Đích đang nhìn thấy được — pill giỏ nổi được ưu tiên vì nó gần nút thêm hơn khi đã cuộn */
const findCartTarget = (): HTMLElement | null => {
  const targets = Array.from(document.querySelectorAll<HTMLElement>('[data-cart-target]'));
  const visible = targets.filter((el) => {
    const rect = el.getBoundingClientRect();
    return rect.width > 0 && rect.bottom > 0 && rect.top < window.innerHeight;
  });
  return visible.find((el) => el.dataset.cartTarget === 'float') ?? visible[0] ?? null;
};

export const flyToCart = (source: Element | null | undefined) => {
  if (!source || prefersReducedMotion() || typeof document.body.animate !== 'function') return;

  const target = findCartTarget();
  if (!target) return;

  const from = source.getBoundingClientRect();
  const to = target.getBoundingClientRect();
  const startX = from.left + from.width / 2 - 23;
  const startY = from.top + from.height / 2 - 10;
  const endX = to.left + to.width / 2 - 23;
  const endY = to.top + to.height / 2 - 10;
  // Đỉnh vòng cung cao hơn điểm cao nhất của hai đầu — trông như ném bánh vào giỏ
  const peakY = Math.min(startY, endY) - 90;
  const midX = startX + (endX - startX) * 0.5;

  const flyer = document.createElement('div');
  flyer.setAttribute('aria-hidden', 'true');
  flyer.innerHTML = MINI_BANH_MI_SVG;
  Object.assign(flyer.style, {
    position: 'fixed',
    left: '0',
    top: '0',
    zIndex: '9999',
    pointerEvents: 'none',
    filter: 'drop-shadow(2px 2px 0 #2B1D14)',
  });
  document.body.appendChild(flyer);

  const animation = flyer.animate(
    [
      { transform: `translate(${startX}px, ${startY}px) scale(.6) rotate(0deg)`, opacity: 0 },
      { transform: `translate(${startX}px, ${startY - 20}px) scale(1.15) rotate(-10deg)`, opacity: 1, offset: 0.15 },
      { transform: `translate(${midX}px, ${peakY}px) scale(1) rotate(-200deg)`, offset: 0.55 },
      { transform: `translate(${endX}px, ${endY}px) scale(.4) rotate(-360deg)`, opacity: 0.9 },
    ],
    { duration: 720, easing: 'cubic-bezier(.45, 0, .55, 1)' },
  );
  const cleanup = () => flyer.remove();
  animation.onfinish = cleanup;
  animation.oncancel = cleanup;
};
