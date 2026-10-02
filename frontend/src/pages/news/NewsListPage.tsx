import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Newspaper } from 'lucide-react';
import { newsApi } from '../../api/newsApi';
import { EmptyState, Pagination, Skeleton } from '../../components/ui';
import { NewsCard } from '../../components/content/NewsCard';
import type { PageResponse } from '../../types/admin';
import type { NewsSummary } from '../../types/content';
import '../../styles/components/card.css';
import '../../styles/components/content.css';

const PAGE_SIZE = 9;

/** /tin-tuc — lưới bài đã đăng (ghim trước, mới nhất trước), phân trang. */
export const NewsListPage = () => {
  const [page, setPage] = useState(1);
  const [data, setData] = useState<PageResponse<NewsSummary> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    newsApi
      .listPublished(page - 1, PAGE_SIZE)
      .then((res) => {
        if (!alive) return;
        setData(res);
        setError(null);
      })
      .catch((err) => {
        if (alive) setError(err instanceof Error ? err.message : 'Không tải được tin tức');
      });
    return () => {
      alive = false;
    };
  }, [page]);

  const changePage = (next: number) => {
    setPage(next);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <div>
      <div className="page-bar">
        <div>
          <p className="page-bar__crumb">
            <Link to="/">Trang chủ</Link> / Tin tức
          </p>
          <h1 className="page-bar__title">Tin tức &amp; khuyến mãi</h1>
        </div>
      </div>

      {error ? (
        <EmptyState icon={<Newspaper size={28} />} title="Không tải được tin tức" description={error} />
      ) : data === null ? (
        <div className="news-grid">
          {Array.from({ length: 3 }, (_, index) => (
            <Skeleton key={index} variant="card" />
          ))}
        </div>
      ) : data.content.length === 0 ? (
        <EmptyState
          icon={<Newspaper size={28} />}
          title="Chưa có tin nào"
          description="Tin tức và chương trình khuyến mãi của cửa hàng sẽ xuất hiện tại đây."
        />
      ) : (
        <>
          <div className="news-grid">
            {data.content.map((news) => (
              <NewsCard key={news.id} news={news} />
            ))}
          </div>
          <Pagination page={page} totalPages={data.totalPages} onChange={changePage} />
        </>
      )}
    </div>
  );
};
