package com.banhmyking.banhmyking.service.impl;

import com.banhmyking.banhmyking.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

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
            log.error("Gửi email {} tới {} thất bại ({}). Link: {}", label, to, e.getMessage(), extractUrl(html));
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
                """.formatted(fullName, intro, actionUrl, buttonLabel, validityNote, actionUrl);
    }
}
