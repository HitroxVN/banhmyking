import { Link } from 'react-router-dom';
import { Newspaper, Pin } from 'lucide-react';
import { formatDate } from '../../utils/formatters';
import type { NewsSummary } from '../../types/content';
import '../../styles/components/content.css';

export interface NewsCardProps {
  news: NewsSummary;
}

/** Thẻ bài: ảnh bìa, ngày đăng, tiêu đề, tóm tắt — dùng cho lưới tin, khối Tin mới, bài liên quan. */
export const NewsCard = ({ news }: NewsCardProps) => (
  <Link to={`/tin-tuc/${news.slug}`} className="news-card">
    <div className="news-card__cover">
      {news.coverImageUrl ? <img src={news.coverImageUrl} alt="" loading="lazy" /> : <Newspaper size={40} />}
    </div>
    <div className="news-card__body">
      <div className="news-card__meta">
        {news.pinned && (
          <>
            <Pin size={13} aria-label="Bài ghim" />
            Nổi bật ·
          </>
        )}
        <span>{formatDate(news.publishedAt)}</span>
      </div>
      <h3 className="news-card__title">{news.title}</h3>
      {news.summary && <p className="news-card__summary">{news.summary}</p>}
    </div>
  </Link>
);
