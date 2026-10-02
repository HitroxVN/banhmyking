import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Newspaper } from 'lucide-react';
import { newsApi } from '../../api/newsApi';
import { NewsCard } from '../content/NewsCard';
import type { NewsSummary } from '../../types/content';
import '../../styles/components/menu.css';
import '../../styles/components/content.css';

/** Khối "Tin mới" trên trang chủ — 3 bài mới nhất; không có bài hoặc lỗi mạng thì ẩn hẳn. */
export const LatestNewsSection = () => {
  const [items, setItems] = useState<NewsSummary[] | null>(null);

  useEffect(() => {
    let alive = true;
    newsApi
      .latest(3)
      .then((res) => {
        if (alive) setItems(res);
      })
      .catch(() => {
        if (alive) setItems([]);
      });
    return () => {
      alive = false;
    };
  }, []);

  if (!items || items.length === 0) return null;

  return (
    <section className="menu__section" id="home-news">
      <div className="menu__section-head">
        <div>
          <h2 className="menu__section-title">
            <Newspaper size={22} />
            Tin mới
          </h2>
          <p className="menu__section-sub">Khuyến mãi và hoạt động mới nhất của cửa hàng</p>
        </div>
        <Link className="menu__section-link" to="/tin-tuc">
          Xem tất cả tin
        </Link>
      </div>
      <div className="news-grid">
        {items.map((news) => (
          <NewsCard key={news.id} news={news} />
        ))}
      </div>
    </section>
  );
};
