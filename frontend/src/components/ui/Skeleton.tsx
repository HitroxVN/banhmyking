import '../../styles/components/skeleton.css';

export type SkeletonVariant = 'text' | 'text-sm' | 'row' | 'card' | 'chart';

export interface SkeletonProps {
  variant?: SkeletonVariant;
  /** Số dòng khung xếp chồng lên nhau */
  count?: number;
  className?: string;
}

export const Skeleton = ({ variant = 'text', count = 1, className = '' }: SkeletonProps) => (
  <span className={`ui-skeleton-stack${className ? ` ${className}` : ''}`}>
    {Array.from({ length: count }, (_, index) => (
      <span key={index} className={`ui-skeleton ui-skeleton--${variant}`} aria-hidden="true" />
    ))}
  </span>
);
