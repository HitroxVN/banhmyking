import { useEffect, useState } from 'react';
import { reviewApi } from '../../api/reviewApi';
import { Button, Modal, Textarea, useToast } from '../ui';
import { RatingStars } from './RatingStars';
import type { OrderItemResponse } from '../../types/order';
import '../../styles/components/review.css';

const MAX_COMMENT = 1000;

export interface ReviewFormModalProps {
  /** Dòng món trong đơn cần đánh giá; null = đóng modal */
  item: OrderItemResponse | null;
  onClose: () => void;
  /** Gọi sau khi gửi thành công để trang cha nạp lại dữ liệu nếu cần */
  onCreated?: () => void;
}

/** Form gửi đánh giá cho 1 món trong đơn đã giao (backend chỉ nhận đơn DELIVERED). */
export const ReviewFormModal = ({ item, onClose, onCreated }: ReviewFormModalProps) => {
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const toast = useToast();

  // Mở món khác thì form phải sạch, tránh gửi nhầm nhận xét của món trước
  useEffect(() => {
    if (item) {
      setRating(5);
      setComment('');
    }
  }, [item]);

  const handleSubmit = async () => {
    if (!item) return;

    setIsSubmitting(true);
    try {
      await reviewApi.createReview({
        orderItemId: item.id,
        rating,
        comment: comment.trim() || undefined,
      });
      toast.success(`Cảm ơn bạn đã đánh giá ${item.productName}`);
      onCreated?.();
      onClose();
    } catch (err) {
      // Backend đã trả message tiếng Việt (đã đánh giá rồi / đơn chưa giao…)
      toast.error(err instanceof Error ? err.message : 'Gửi đánh giá thất bại');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      open={item !== null}
      onClose={onClose}
      title="Đánh giá món"
      size="sm"
      footer={
        <div className="rvw-form__foot">
          <Button variant="secondary" onClick={onClose} disabled={isSubmitting}>
            Để sau
          </Button>
          <Button loading={isSubmitting} onClick={handleSubmit}>
            Gửi đánh giá
          </Button>
        </div>
      }
    >
      {item && (
        <div className="rvw-form">
          <div className="rvw-form__item">
            {item.productImageUrl && (
              <img className="rvw-form__img" src={item.productImageUrl} alt={item.productName} loading="lazy" />
            )}
            <span className="rvw-form__name">{item.productName}</span>
          </div>

          <div className="rvw-form__field">
            <span className="rvw-form__label">Bạn cho món này mấy sao?</span>
            <RatingStars value={rating} onChange={setRating} size={26} label="Chọn số sao" />
          </div>

          <Textarea
            label="Nhận xét (không bắt buộc)"
            value={comment}
            rows={4}
            maxLength={MAX_COMMENT}
            placeholder="Bánh có giòn không, nhân có đầy không…"
            hint={`${comment.length}/${MAX_COMMENT} ký tự`}
            onChange={(event) => setComment(event.target.value)}
          />
        </div>
      )}
    </Modal>
  );
};
