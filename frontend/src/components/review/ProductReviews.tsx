import { useEffect, useState } from 'react';
import { reviewApi } from '../../api/reviewApi';
import { Button, Spinner } from '../ui';
import { RatingStars } from './RatingStars';
import { formatDateTime } from '../../utils/formatters';
import type { ProductRatingSummary, ReviewResponse } from '../../types/review';
import '../../styles/components/review.css';

const PAGE_SIZE = 5;

export interface ProductReviewsProps {
  productId: number;
}

/** Khối đánh giá trong modal chi tiết món: điểm trung bình + danh sách nhận xét thật từ backend. */
export const ProductReviews = ({ productId }: ProductReviewsProps) => {
  const [summary, setSummary] = useState<ProductRatingSummary | null>(null);
  const [reviews, setReviews] = useState<ReviewResponse[]>([]);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isLoadingMore, setIsLoadingMore] = useState(false);

  // Mỗi món một khối riêng nên nạp lại từ trang đầu khi đổi món
  useEffect(() => {
    let cancelled = false;
    setIsLoading(true);
    setPage(0);

    Promise.all([reviewApi.getProductRating(productId), reviewApi.getProductReviews(productId, 0, PAGE_SIZE)])
      .then(([rating, list]) => {
        if (cancelled) return;
        setSummary(rating);
        setReviews(list.content);
        setHasMore(!list.last);
      })
      .catch(() => {
        // Đánh giá là thông tin phụ — lỗi thì ẩn khối, không chặn việc mua món
        if (cancelled) return;
        setSummary(null);
        setReviews([]);
        setHasMore(false);
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [productId]);

  const loadMore = async () => {
    setIsLoadingMore(true);
    try {
      const next = await reviewApi.getProductReviews(productId, page + 1, PAGE_SIZE);
      setReviews((prev) => [...prev, ...next.content]);
      setPage((prev) => prev + 1);
      setHasMore(!next.last);
    } catch {
      // giữ nguyên danh sách đang có, khách bấm lại được
    } finally {
      setIsLoadingMore(false);
    }
  };

  if (isLoading) {
    return (
      <section className="rvw">
        <h3 className="rvw__title">Khách đánh giá</h3>
        <div className="rvw__loading">
          <Spinner size={20} />
        </div>
      </section>
    );
  }

  const total = summary?.totalReviews ?? 0;
  const average = summary?.averageRating ?? 0;

  return (
    <section className="rvw">
      <div className="rvw__head">
        <h3 className="rvw__title">Khách đánh giá</h3>
        {total > 0 && (
          <span className="rvw__score">
            <strong>{average.toFixed(1)}</strong>
            <RatingStars value={average} size={14} label={`${average.toFixed(1)} trên 5`} />
            <span className="rvw__count">{total} đánh giá</span>
          </span>
        )}
      </div>

      {total === 0 ? (
        <p className="rvw__empty">Món này chưa có đánh giá nào.</p>
      ) : (
        <>
          <ul className="rvw__list">
            {reviews.map((review) => (
              <li className="rvw__item" key={review.id}>
                <div className="rvw__item-top">
                  <strong className="rvw__name">{review.userFullName || 'Khách hàng'}</strong>
                  <RatingStars value={review.rating} size={13} label={`${review.rating} trên 5`} />
                </div>
                {review.comment && <p className="rvw__comment">{review.comment}</p>}
                <span className="rvw__date">{formatDateTime(review.createdAt)}</span>
              </li>
            ))}
          </ul>

          {hasMore && (
            <Button variant="secondary" size="sm" loading={isLoadingMore} onClick={loadMore}>
              Xem thêm đánh giá
            </Button>
          )}
        </>
      )}
    </section>
  );
};
