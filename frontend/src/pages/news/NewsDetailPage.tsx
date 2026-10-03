import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Newspaper } from 'lucide-react';
import { newsApi } from '../../api/newsApi';
import { Button, EmptyState, Skeleton } from '../../components/ui';
import type { ApiError } from '../../api/axiosClient';
import { MarkdownView } from '../../components/content/MarkdownView';
import { NewsCard } from '../../components/content/NewsCard';
import { formatDateTime } from '../../utils/formatters';
import type { NewsDetail } from '../../types/content';
import '../../styles/components/card.css';
import '../../styles/components/content.css';

interface LoadState {
  slug: string;
  news: NewsDetail | null;
  /** 'notfound' = 404 (bài không tồn tại/chưa đăng); 'error' = mạng/5xx → cho thử lại */
  error: 'notfound' | 'error' | null;
  message: string | null;
}

/** /tin-tuc/:slug — bài đã đăng + tối đa 3 bài liên quan. Bài nháp/hẹn giờ: backend trả 404. */
export const NewsDetailPage = () => {
  const { slug = '' } = useParams();
  const [state, setState] = useState<LoadState | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let alive = true;
    newsApi
      .getBySlug(slug)
      .then((news) => {
        if (alive) setState({ slug, news, error: null, message: null });
      })
      .catch((err) => {
        if (!alive) return;
        const status = (err as ApiError | undefined)?.status;
        setState({
          slug,
          news: null,
          error: status === 404 ? 'notfound' : 'error',
          message: err instanceof Error ? err.message : null,
        });
      });
    return () => {
      alive = false;
    };
  }, [slug, reloadKey]);

  if (state === null || state.slug !== slug) {
    return (
      <div className="news-article">
        <Skeleton variant="card" />
      </div>
    );
  }

  if (!state.news) {
    if (state.error === 'error') {
      return (
        <EmptyState
          icon={<Newspaper size={28} />}
          title="Không tải được bài viết"
          description={state.message ?? 'Đã có lỗi xảy ra, vui lòng thử lại.'}
          action={
            <Button
              onClick={() => {
                setState(null);
                setReloadKey((key) => key + 1);
              }}
            >
              Thử lại
            </Button>
          }
        />
      );
    }
    return (
      <EmptyState
        icon={<Newspaper size={28} />}
        title="Không tìm thấy bài viết"
        description="Bài viết không tồn tại hoặc chưa được đăng."
        action={
          <Link className="ui-btn ui-btn--primary ui-btn--md" to="/tin-tuc">
            Xem tất cả tin tức
          </Link>
        }
      />
    );
  }

  const news = state.news;
  return (
    <div>
      <div className="page-bar">
        <div>
          <p className="page-bar__crumb">
            <Link to="/">Trang chủ</Link> / <Link to="/tin-tuc">Tin tức</Link> / {news.title}
          </p>
        </div>
      </div>

      <article className="card news-article">
        <div className="card__body">
          {news.coverImageUrl && <img className="news-article__cover" src={news.coverImageUrl} alt="" />}
          <h1 className="page-bar__title">{news.title}</h1>
          <p className="news-article__date">Đăng lúc {formatDateTime(news.publishedAt)}</p>
          <MarkdownView source={news.content} />
        </div>
      </article>

      {news.related.length > 0 && (
        <section className="news-related">
          <h2>Bài liên quan</h2>
          <div className="news-grid">
            {news.related.map((item) => (
              <NewsCard key={item.id} news={item} />
            ))}
          </div>
        </section>
      )}
    </div>
  );
};
