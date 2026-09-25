import { useCallback, useEffect, useState } from 'react';
import { MessageSquare, Trash2, XCircle } from 'lucide-react';
import { Button, EmptyState, PageHeader, Pagination, Select, Skeleton, useConfirm, useToast } from '../components/ui';
import { RatingStars } from '../components/review/RatingStars';
import { reviewApi } from '../api/reviewApi';
import { staffCatalogApi } from '../api/staffCatalogApi';
import { useAuth } from '../context/useAuth';
import { formatDateTime } from '../utils/formatters';
import type { ReviewResponse } from '../types/review';
import type { ProductItem } from '../types/staff';
import '../styles/components/review-manager.css';

const PAGE_SIZE = 10;
const RATING_OPTIONS = [5, 4, 3, 2, 1];

/**
 * Trang quản lý đánh giá — dùng chung cho /admin/reviews và /staff/reviews.
 * STAFF chỉ xem (để xử lý phàn nàn), chỉ ADMIN thấy nút Xoá; backend chốt quyền thật.
 */
export const ReviewManagerPage = () => {
  const { user } = useAuth();
  const canDelete = user?.role === 'ADMIN';

  const [reviews, setReviews] = useState<ReviewResponse[]>([]);
  const [products, setProducts] = useState<ProductItem[]>([]);
  const [productFilter, setProductFilter] = useState('ALL');
  const [ratingFilter, setRatingFilter] = useState('ALL');
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);

  const confirm = useConfirm();
  const toast = useToast();

  const load = useCallback(async () => {
    setIsLoading(true);
    setErrorMsg(null);
    try {
      const data = await reviewApi.getAllReviews({
        productId: productFilter === 'ALL' ? undefined : Number(productFilter),
        rating: ratingFilter === 'ALL' ? undefined : Number(ratingFilter),
        page: page - 1,
        size: PAGE_SIZE,
      });
      setReviews(data.content);
      setTotalPages(Math.max(1, data.totalPages));
    } catch (err: unknown) {
      setErrorMsg(err instanceof Error ? err.message : 'Không tải được danh sách đánh giá.');
    } finally {
      setIsLoading(false);
    }
  }, [productFilter, ratingFilter, page]);

  useEffect(() => {
    void load();
  }, [load]);

  // Danh sách món cho bộ lọc — lấy cả món đã hết vì đánh giá cũ vẫn còn
  useEffect(() => {
    staffCatalogApi
      .getProducts()
      .then(setProducts)
      .catch(() => setProducts([]));
  }, []);

  const hasFilter = productFilter !== 'ALL' || ratingFilter !== 'ALL';

  const handleDelete = async (review: ReviewResponse) => {
    const ok = await confirm({
      title: 'Xoá đánh giá',
      message: `Xoá đánh giá ${review.rating} sao của "${review.userFullName ?? 'khách hàng'}" cho món ${review.productName}? Điểm sao của món sẽ tính lại và khách được đánh giá lại món này.`,
      confirmText: 'Xoá đánh giá',
      danger: true,
    });
    if (!ok) return;

    setDeletingId(review.id);
    try {
      await reviewApi.deleteReview(review.id);
      toast.success('Đã xoá đánh giá');
      // Xoá bản ghi cuối của trang thì lùi 1 trang để không nhìn vào trang rỗng
      if (reviews.length === 1 && page > 1) setPage(page - 1);
      else await load();
    } catch (err: unknown) {
      toast.error(err instanceof Error ? err.message : 'Xoá đánh giá thất bại.');
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <>
      <PageHeader
        title="Đánh giá của khách"
        subtitle={
          canDelete
            ? 'Xem và xoá đánh giá không phù hợp — điểm sao của món tính lại ngay sau khi xoá'
            : 'Chỉ xem để xử lý phàn nàn — cần xoá đánh giá thì nhờ quản trị viên'
        }
      />

      <div className="rmgr__filters">
        <Select
          label="Món"
          value={productFilter}
          onChange={(event) => {
            setProductFilter(event.target.value);
            setPage(1);
          }}
        >
          <option value="ALL">Tất cả món</option>
          {products.map((product) => (
            <option key={product.id} value={product.id}>
              {product.name}
            </option>
          ))}
        </Select>

        <Select
          label="Số sao"
          value={ratingFilter}
          onChange={(event) => {
            setRatingFilter(event.target.value);
            setPage(1);
          }}
        >
          <option value="ALL">Tất cả mức sao</option>
          {RATING_OPTIONS.map((star) => (
            <option key={star} value={star}>
              {star} sao
            </option>
          ))}
        </Select>

        {hasFilter && (
          <Button
            variant="ghost"
            onClick={() => {
              setProductFilter('ALL');
              setRatingFilter('ALL');
              setPage(1);
            }}
          >
            Xoá lọc
          </Button>
        )}
      </div>

      {errorMsg && (
        <div className="alert-banner alert-error page-alert" role="alert">
          <XCircle size={18} />
          <div>{errorMsg}</div>
        </div>
      )}

      <section className="card">
        {isLoading ? (
          <div className="card__body">
            <Skeleton variant="row" count={5} />
          </div>
        ) : reviews.length === 0 ? (
          <div className="card__body">
            <EmptyState
              icon={<MessageSquare size={30} />}
              title={hasFilter ? 'Không có đánh giá nào khớp bộ lọc' : 'Chưa có đánh giá nào'}
              description={
                hasFilter
                  ? 'Thử bỏ lọc để xem toàn bộ đánh giá của khách.'
                  : 'Đánh giá sẽ xuất hiện ở đây sau khi khách nhận hàng và chấm điểm món.'
              }
            />
          </div>
        ) : (
          <div className="table-wrap">
            <table className="ui-table">
              <thead>
                <tr>
                  <th>Món</th>
                  <th>Khách</th>
                  <th>Điểm</th>
                  <th>Nhận xét</th>
                  <th>Ngày</th>
                  {canDelete && <th aria-label="Hành động" />}
                </tr>
              </thead>
              <tbody>
                {reviews.map((review) => (
                  <tr key={review.id}>
                    <td>
                      <span className="ui-table__primary">{review.productName}</span>
                      <span className="ui-table__meta">Mã món #{review.productId}</span>
                    </td>
                    <td>{review.userFullName || 'Khách hàng'}</td>
                    <td>
                      <RatingStars value={review.rating} size={14} label={`${review.rating} trên 5`} />
                    </td>
                    <td className="ui-table__clip">
                      {review.comment ? (
                        <span className="rmgr__comment">{review.comment}</span>
                      ) : (
                        <span className="ui-table__meta">Không có nhận xét</span>
                      )}
                    </td>
                    <td className="rmgr__date">{formatDateTime(review.createdAt)}</td>
                    {canDelete && (
                      <td>
                        <div className="ui-table__actions">
                          <Button
                            size="sm"
                            variant="ghost"
                            icon={<Trash2 size={15} />}
                            loading={deletingId === review.id}
                            onClick={() => void handleDelete(review)}
                          >
                            Xoá
                          </Button>
                        </div>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {!isLoading && totalPages > 1 && (
          <div className="rmgr__pager">
            <Pagination page={page} totalPages={totalPages} onChange={setPage} />
          </div>
        )}
      </section>
    </>
  );
};
