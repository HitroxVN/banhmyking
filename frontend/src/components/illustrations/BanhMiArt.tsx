import '../../styles/components/illustrations.css';

const INK = '#2B1D14';

/**
 * Ổ bánh mì vẽ tay (SVG thuần, không cần ảnh ngoài) — vỏ vàng, pate, chả, rau, dưa leo,
 * đồ chua, ớt và làn hơi nóng bốc lên. Dùng làm hình hero khi admin chưa tải ảnh banner.
 */
export const BanhMiArt = ({ className = '' }: { className?: string }) => (
  <svg
    className={`ill-banhmi ${className}`}
    viewBox="0 0 320 210"
    role="img"
    aria-label="Ổ bánh mì nóng giòn nhân đầy"
    fill="none"
    strokeLinecap="round"
    strokeLinejoin="round"
  >
    {/* Bóng đổ dưới ổ bánh */}
    <ellipse cx="160" cy="186" rx="124" ry="10" fill={INK} opacity=".14" />

    {/* Hơi nóng */}
    <g className="ill-banhmi__steam" stroke={INK} strokeWidth="3" opacity=".45">
      <path d="M118 40 q-9 -10 0 -19 q9 -9 0 -18" />
      <path d="M160 34 q-9 -10 0 -19 q9 -9 0 -18" />
      <path d="M202 40 q-9 -10 0 -19 q9 -9 0 -18" />
    </g>

    {/* Nửa dưới ổ bánh */}
    <path
      d="M28 128 C28 154 62 172 160 172 C258 172 292 154 292 128 Z"
      fill="#D98A2B"
      stroke={INK}
      strokeWidth="3.5"
    />
    <path d="M36 128 H284" stroke="#FBE3B0" strokeWidth="7" />

    {/* Đồ chua (cà rốt, củ cải) ló ra đầu trái */}
    <g strokeWidth="5">
      <path d="M44 118 L14 104" stroke="#F08A24" />
      <path d="M46 124 L18 120" stroke="#FFF4D6" />
      <path d="M48 112 L24 94" stroke="#F08A24" />
    </g>

    {/* Chả lụa + thịt nguội */}
    <path
      d="M44 124 Q160 104 278 124 L276 132 Q160 116 46 132 Z"
      fill="#E9A196"
      stroke={INK}
      strokeWidth="3"
    />

    {/* Dưa leo ló ra đầu phải */}
    <g transform="rotate(-18 286 112)">
      <ellipse cx="286" cy="112" rx="20" ry="7" fill="#C7E6A8" stroke={INK} strokeWidth="3" />
      <path d="M268 112 H304" stroke="#4C8C3F" strokeWidth="2" />
    </g>

    {/* Xà lách lượn sóng */}
    <path
      d="M38 122 Q50 102 62 120 Q74 100 88 120 Q102 100 116 120 Q130 100 144 120 Q158 100 172 120 Q186 100 200 120 Q214 100 228 120 Q242 100 256 120 Q270 102 284 122 Z"
      fill="#7DB85A"
      stroke={INK}
      strokeWidth="3"
    />

    {/* Nửa trên ổ bánh */}
    <path
      d="M22 104 C22 62 82 44 160 44 C238 44 298 62 298 104 C298 111 291 113 283 111 C212 98 108 98 37 111 C29 113 22 111 22 104 Z"
      fill="#E8A33D"
      stroke={INK}
      strokeWidth="3.5"
    />
    {/* Khía vỏ bánh */}
    <g stroke="#FBE3B0" strokeWidth="5">
      <path d="M86 70 q16 -12 34 -5" />
      <path d="M140 60 q16 -12 34 -5" />
      <path d="M196 64 q16 -12 34 -3" />
    </g>
    <path d="M52 84 Q64 66 92 58" stroke="#FFF4D6" strokeWidth="4" opacity=".7" />

    {/* Ớt lát + rau mùi rắc trên cùng */}
    <g stroke={INK} strokeWidth="2.5">
      <circle cx="112" cy="112" r="7" fill="#C8432B" />
      <circle cx="196" cy="110" r="7" fill="#C8432B" />
      <circle cx="244" cy="114" r="6" fill="#C8432B" />
    </g>
    <g fill="#FFE2C2">
      <circle cx="112" cy="112" r="2.4" />
      <circle cx="196" cy="110" r="2.4" />
      <circle cx="244" cy="114" r="2" />
    </g>
    <g fill="#3F7D3A" stroke={INK} strokeWidth="2">
      <path d="M150 104 q8 -14 18 -6 q-4 10 -18 6 Z" />
      <path d="M70 108 q6 -14 18 -8 q-4 12 -18 8 Z" />
      <path d="M262 102 q10 -10 18 0 q-8 8 -18 0 Z" />
    </g>
  </svg>
);

/** Ổ bánh mì tí hon — "bay" vào giỏ khi thêm món và làm icon trang trí nhỏ. */
export const MiniBanhMi = ({ size = 48 }: { size?: number }) => (
  <svg width={size} height={(size * 22) / 48} viewBox="0 0 48 22" fill="none" aria-hidden="true">
    <path d="M3 12 C3 18 10 20 24 20 C38 20 45 18 45 12 Z" fill="#D98A2B" stroke={INK} strokeWidth="1.6" />
    <path d="M4 12 Q9 8 14 12 Q19 8 24 12 Q29 8 34 12 Q39 8 44 12" fill="#7DB85A" stroke={INK} strokeWidth="1.4" />
    <circle cx="19" cy="11" r="1.6" fill="#C8432B" />
    <circle cx="31" cy="11" r="1.6" fill="#C8432B" />
    <path
      d="M1.5 11 C1.5 4.5 11 2 24 2 C37 2 46.5 4.5 46.5 11 C38 9 10 9 1.5 11 Z"
      fill="#E8A33D"
      stroke={INK}
      strokeWidth="1.6"
    />
    <path d="M13 6 q3 -2 6 -.6 M22 5 q3 -2 6 -.6 M31 5.5 q3 -2 6 -.4" stroke="#FBE3B0" strokeWidth="1.4" strokeLinecap="round" />
  </svg>
);
