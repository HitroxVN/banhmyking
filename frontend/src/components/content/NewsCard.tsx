import { Link } from 'react-router-dom';
import { ArrowRight, Pin } from 'lucide-react';
import { formatDate } from '../../utils/formatters';
import type { NewsSummary } from '../../types/content';
import { MiniBanhMi } from '../illustrations/BanhMiArt';
import '../../styles/components/content.css';

export interface NewsCardProps {
  news: NewsSummary;
}

/** Thẻ bài: ảnh bìa, ngày đăng, tiêu đề, tóm tắt — dùng cho lưới tin, khối Tin mới, bài liên quan. */
export const NewsCard = ({ news }: NewsCardProps) => (
  <Link to={`/tin-tuc/${news.slug}`} className="news-card">
    <div className="news-card__cover">
      {news.coverImageUrl ? (
        <img src={news.coverImageUrl} alt="" loading="lazy" />
      ) : (
        <span className="news-card__placeholder">
          <MiniBanhMi size={96} />
        </span>
      )}
      {news.pinned && (
        <span className="news-card__pin">
          <Pin size={12} aria-label="Bài ghim" />
          Nổi bật
        </span>
      )}
    </div>
    <div className="news-card__body">
      <div className="news-card__meta">
        <span className="news-card__date">{formatDate(news.publishedAt)}</span>
      </div>
      <h3 className="news-card__title">{news.title}</h3>
      {news.summary && <p className="news-card__summary">{news.summary}</p>}
      <span className="news-card__more" aria-hidden="true">
        Đọc tiếp <ArrowRight size={14} />
      </span>
    </div>
  </Link>
);
