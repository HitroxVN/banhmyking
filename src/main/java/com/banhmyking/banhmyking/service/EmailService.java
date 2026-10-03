package com.banhmyking.banhmyking.service;

/** Gửi email giao dịch. Chỉ một cách gửi duy nhất, không cần interface trừu tượng hoá thêm. */
public interface EmailService {

    /** Gửi mail chứa link xác thực tài khoản. Lỗi SMTP chỉ được log, KHÔNG ném ra ngoài. */
    void sendVerificationEmail(String to, String fullName, String rawToken);

    /** Gửi mail chứa link đặt lại mật khẩu. Lỗi SMTP chỉ được log, KHÔNG ném ra ngoài. */
    void sendPasswordResetEmail(String to, String fullName, String rawToken);

    /** Xác nhận đã nhận hồ sơ ứng tuyển (spec D §4). Lỗi SMTP chỉ được log, KHÔNG ném ra ngoài. */
    void sendApplicationConfirmation(String to, String fullName, String jobTitle, String storeName);

    /** Báo email cấu hình "contactEmail" có phản hồi mới (spec D §5). Lỗi SMTP chỉ được log. */
    void sendFeedbackNotice(String to, com.banhmyking.banhmyking.dto.feedback.FeedbackNotice notice);
}
