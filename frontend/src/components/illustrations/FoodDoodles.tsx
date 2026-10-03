import '../../styles/components/illustrations.css';

const INK = '#2B1D14';

interface DoodleProps {
  size?: number;
  className?: string;
}

/** Lá rau mùi — trang trí trôi quanh hero */
export const LeafDoodle = ({ size = 36, className = '' }: DoodleProps) => (
  <svg className={className} width={size} height={size} viewBox="0 0 36 36" fill="none" aria-hidden="true">
    <path d="M6 30 C6 14 16 5 31 5 C31 20 22 30 6 30 Z" fill="#7DB85A" stroke={INK} strokeWidth="2" strokeLinejoin="round" />
    <path d="M7 29 L24 12 M14 22 L14 15 M19 17 L25 17" stroke={INK} strokeWidth="1.6" strokeLinecap="round" />
  </svg>
);

/** Lát ớt đỏ cắt ngang */
export const ChiliDoodle = ({ size = 30, className = '' }: DoodleProps) => (
  <svg className={className} width={size} height={size} viewBox="0 0 30 30" fill="none" aria-hidden="true">
    <circle cx="15" cy="15" r="12" fill="#C8432B" stroke={INK} strokeWidth="2" />
    <circle cx="15" cy="15" r="6.5" fill="#F3B4A3" />
    <g fill="#FFF4D6">
      <circle cx="13" cy="13" r="1.5" />
      <circle cx="17.5" cy="14" r="1.5" />
      <circle cx="14.5" cy="17.5" r="1.5" />
    </g>
  </svg>
);

/** Lát dưa leo */
export const CucumberDoodle = ({ size = 34, className = '' }: DoodleProps) => (
  <svg className={className} width={size} height={size} viewBox="0 0 34 34" fill="none" aria-hidden="true">
    <circle cx="17" cy="17" r="14" fill="#4C8C3F" stroke={INK} strokeWidth="2" />
    <circle cx="17" cy="17" r="11" fill="#D8EBCF" />
    <g fill="#8DBF77">
      <ellipse cx="17" cy="12" rx="1.6" ry="2.4" />
      <ellipse cx="12.5" cy="19" rx="1.6" ry="2.4" transform="rotate(-60 12.5 19)" />
      <ellipse cx="21.5" cy="19" rx="1.6" ry="2.4" transform="rotate(60 21.5 19)" />
    </g>
  </svg>
);

/**
 * Đĩa trống còn vài vụn bánh — cho trạng thái "chưa có gì" (giỏ rỗng, không tìm thấy món,
 * trang 404). Cố ý vui vẻ thay vì một icon xám.
 */
export const EmptyPlate = ({ size = 160, className = '' }: DoodleProps) => (
  <svg
    className={`ill-plate ${className}`}
    width={size}
    height={(size * 120) / 160}
    viewBox="0 0 160 120"
    fill="none"
    aria-hidden="true"
  >
    <ellipse cx="80" cy="106" rx="62" ry="8" fill={INK} opacity=".12" />
    <ellipse cx="80" cy="84" rx="70" ry="24" fill="#FFFDF7" stroke={INK} strokeWidth="3" />
    <ellipse cx="80" cy="82" rx="48" ry="14" stroke="#D5CBBD" strokeWidth="2.5" strokeDasharray="5 6" />
    <g fill="#D98A2B" stroke={INK} strokeWidth="1.5">
      <path d="M62 80 l6 -3 l3 5 l-6 2 Z" />
      <path d="M92 84 l5 -2 l2 4 l-5 2 Z" />
    </g>
    <circle cx="80" cy="78" r="2.2" fill="#D98A2B" />
    <circle cx="104" cy="78" r="1.8" fill="#D98A2B" />
    {/* Nĩa nghiêng bên phải */}
    <g className="ill-plate__fork" stroke={INK} strokeWidth="3" strokeLinecap="round">
      <path d="M138 20 L126 62" />
      <path d="M132 14 L130 30 M138 14 L136 30 M144 15 L141 31" strokeWidth="2.4" />
      <path d="M130 30 Q136 34 141 31" strokeWidth="2.4" />
    </g>
    {/* Dấu hỏi nhỏ trôi phía trên */}
    <text x="58" y="40" className="ill-plate__q" fontFamily="'Paytone One', sans-serif" fontSize="30" fill="#B83A24">
      ?
    </text>
  </svg>
);
