package com.banhmyking.banhmyking.service.impl;

import com.banhmyking.banhmyking.dto.feedback.FeedbackNotice;
import com.banhmyking.banhmyking.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

/**
 * Gửi mail qua SMTP (Gmail). Cấu hình ở spring.mail.* + MAIL_USERNAME / MAIL_PASSWORD.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    /** Địa chỉ gửi — Gmail bắt buộc trùng tài khoản SMTP. */
    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Value("${app.verification-token-expiry-hours}")
    private int verificationExpiryHours;

    @Value("${app.reset-token-expiry-minutes}")
    private int resetExpiryMinutes;

    @Override
    public void sendVerificationEmail(String to, String fullName, String rawToken) {
        send(to, "Xác thực tài khoản Bánh Mỳ King",
                buildHtml(fullName,
                        "Cảm ơn bạn đã đăng ký tài khoản. Bấm vào nút bên dưới để xác thực email và bắt đầu đặt hàng:",
                        "Xác thực email",
                        frontendBaseUrl + "/verify-email?token=" + rawToken,
                        "Link có hiệu lực trong " + verificationExpiryHours + " giờ"),
                "email xác thực");
    }

    @Override
    public void sendPasswordResetEmail(String to, String fullName, String rawToken) {
        send(to, "Đặt lại mật khẩu Bánh Mỳ King",
                buildHtml(fullName,
                        "Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn. Bấm vào nút bên dưới để chọn mật khẩu mới:",
                        "Đặt lại mật khẩu",
                        frontendBaseUrl + "/reset-password?token=" + rawToken,
                        "Link có hiệu lực trong " + resetExpiryMinutes + " phút"),
                "đặt lại mật khẩu");
    }

    @Override
    public void sendApplicationConfirmation(String to, String fullName, String jobTitle, String storeName) {
        String body = "Cảm ơn bạn đã ứng tuyển vị trí <strong>%s</strong> tại <strong>%s</strong>. "
                .formatted(HtmlUtils.htmlEscape(jobTitle), HtmlUtils.htmlEscape(storeName))
                + "Cửa hàng sẽ xem hồ sơ và liên hệ với bạn qua số điện thoại đã đăng ký trong thời gian sớm nhất.";
        send(to, "Bánh Mỳ King đã nhận hồ sơ ứng tuyển của bạn", buildInfoHtml(fullName, body),
                "xác nhận ứng tuyển");
    }

    @Override
    public void sendFeedbackNotice(String to, FeedbackNotice notice) {
        String rows = infoRow("Loại", notice.typeLabel())
                + infoRow("Người gửi", notice.senderName())
                + infoRow("Điện thoại", notice.phone())
                + infoRow("Email", notice.email())
                + infoRow("Cơ sở", notice.storeName() == null ? "Chung toàn chuỗi" : notice.storeName())
                + infoRow("Đơn hàng", notice.orderCode());
        String html = """
                <div style="font-family:Arial,'Helvetica Neue',sans-serif;max-width:560px;margin:0 auto;padding:24px;color:#2b1a0e">
                  <h2 style="color:#b45309;margin:0 0 12px">Phản hồi mới: %s</h2>
                  <table style="border-collapse:collapse;font-size:14px;margin-bottom:12px">%s</table>
                  <p style="white-space:pre-wrap;border-left:3px solid #f59e0b;padding-left:12px">%s</p>
                  <p><a href="%s" style="color:#b45309">Mở mục Phản hồi trong trang quản trị</a></p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(notice.subject()), rows, HtmlUtils.htmlEscape(notice.content()),
                frontendBaseUrl + "/admin/feedbacks");
        send(to, "[Phản hồi mới] " + notice.subject(), html, "báo phản hồi mới");
    }

    private static String infoRow(String label, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return "<tr><td style=\"padding:4px 12px 4px 0;color:#6b7280\">%s</td><td><strong>%s</strong></td></tr>"
                .formatted(label, HtmlUtils.htmlEscape(value));
    }

    /** Email chỉ có lời nhắn (không nút bấm). body đã được escape phần dữ liệu người dùng. */
    private String buildInfoHtml(String fullName, String body) {
        return """
                <div style="font-family:Arial,'Helvetica Neue',sans-serif;max-width:520px;margin:0 auto;padding:24px;color:#2b1a0e">
                  <h2 style="color:#b45309;margin:0 0 8px">BÁNH MỲ KING</h2>
                  <p>Xin chào <strong>%s</strong>,</p>
                  <p>%s</p>
                  <p style="font-size:13px;color:#6b7280">Email này được gửi tự động, vui lòng không trả lời.</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(fullName == null ? "" : fullName), body);
    }

    /**
     * Lỗi SMTP chỉ được log, KHÔNG ném ra ngoài: không được làm hỏng thao tác
     * đăng ký / quên mật khẩu mà user đã thực hiện thành công ở tầng DB.
     * Log kèm link để DEV local chưa cấu hình SMTP vẫn làm tiếp được bằng tay.
     */
    private void send(String to, String subject, String html, String label) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Đã gửi email {} tới {}", label, to);
        } catch (MailException | jakarta.mail.MessagingException e) {
            // Link chứa token thô (đặt lại mật khẩu được) → không đưa vào log ERROR thường trực.
            // DEV chưa cấu hình SMTP: bật logging.level.com.banhmyking.banhmyking.service.impl.EmailServiceImpl=DEBUG để xem link.
            log.error("Gửi email {} tới {} thất bại ({})", label, to, e.getMessage());
            log.debug("Link trong email {} tới {}: {}", label, to, extractUrl(html));
        }
    }

    /** Lấy href đầu tiên trong HTML để in kèm log khi gửi lỗi. */
    private String extractUrl(String html) {
        int start = html.indexOf("href=\"");
        if (start < 0) {
            return "(không có)";
        }
        int from = start + "href=\"".length();
        return html.substring(from, html.indexOf('"', from));
    }

    private String buildHtml(String fullName, String intro, String buttonLabel, String actionUrl, String validityNote) {
        return """
                <div style="font-family:Arial,'Helvetica Neue',sans-serif;max-width:520px;margin:0 auto;padding:24px;color:#2b1a0e">
                  <h2 style="color:#b45309;margin:0 0 8px">BÁNH MỲ KING</h2>
                  <p>Xin chào <strong>%s</strong>,</p>
                  <p>%s</p>
                  <p style="text-align:center;margin:28px 0">
                    <a href="%s" style="background:#b45309;color:#fff;text-decoration:none;padding:12px 28px;border-radius:8px;display:inline-block">%s</a>
                  </p>
                  <p style="font-size:13px;color:#6b7280">%s. Nếu nút không bấm được, dán đường dẫn này vào trình duyệt:<br>%s</p>
                  <p style="font-size:13px;color:#6b7280">Nếu bạn không thực hiện yêu cầu này, hãy bỏ qua email.</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(fullName == null ? "" : fullName), intro, actionUrl, buttonLabel, validityNote, actionUrl);
    }
}
