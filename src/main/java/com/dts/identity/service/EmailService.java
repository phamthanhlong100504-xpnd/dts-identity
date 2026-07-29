package com.dts.identity.service;

import com.dts.identity.entity.VerificationCode.VerificationType;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Service xử lý gửi Email xác thực (OTP / Link) bất đồng bộ qua SMTP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    @Value("${app.mail.from:noreply@dts.com}")
    private String mailFrom;

    @Value("${app.mail.sender-name:DTS Identity}")
    private String senderName;

    /**
     * Gửi email OTP bất đồng bộ cho người dùng.
     *
     * @param toEmail   Email người nhận
     * @param username  Tên người dùng
     * @param otp       Mã OTP 6 chữ số
     * @param type      Loại xác thực (REGISTER, RESET_PASSWORD, CHANGE_EMAIL, etc.)
     */
    @Async
    public void sendOtpEmailAsync(String toEmail, String username, String otp, VerificationType type) {
        if (!mailEnabled) {
            log.info("Email service is disabled. Skipping OTP email for: {}", toEmail);
            return;
        }

        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Cannot send OTP email: recipient email is null or blank");
            return;
        }

        try {
            String subject = getSubjectByType(type);
            String htmlContent = buildOtpHtmlTemplate(username, otp, type);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

            helper.setFrom(mailFrom, senderName);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Successfully sent OTP email to: {} [Type: {}]", toEmail, type);

        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage(), e);
            // Đã catch exception để không phá vỡ giao dịch của người dùng
        }
    }

    private String getSubjectByType(VerificationType type) {
        return switch (type) {
            case REGISTER -> "[DTS Identity] Mã xác thực đăng ký tài khoản";
            case RESET_PASSWORD -> "[DTS Identity] Mã xác thực đặt lại mật khẩu";
            case CHANGE_EMAIL -> "[DTS Identity] Mã xác thực thay đổi email";
            default -> "[DTS Identity] Mã xác thực tài khoản";
        };
    }

    private String buildOtpHtmlTemplate(String username, String otp, VerificationType type) {
        String title = switch (type) {
            case REGISTER -> "Xác thực đăng ký tài khoản";
            case RESET_PASSWORD -> "Yêu cầu đặt lại mật khẩu";
            case CHANGE_EMAIL -> "Xác nhận thay đổi email";
            default -> "Mã xác thực tài khoản";
        };

        String messageDetail = switch (type) {
            case REGISTER -> "Cảm ơn bạn đã đăng ký tài khoản tại DTS Identity. Sử dụng mã OTP bên dưới để hoàn tất xác thực email.";
            case RESET_PASSWORD -> "Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn. Sử dụng mã OTP bên dưới để thực hiện.";
            case CHANGE_EMAIL -> "Sử dụng mã OTP bên dưới để xác nhận thay đổi địa chỉ email cho tài khoản của bạn.";
            default -> "Sử dụng mã OTP bên dưới để hoàn tất thao tác của bạn.";
        };

        String name = (username != null && !username.isBlank()) ? username : "Người dùng";

        return """
            <!DOCTYPE html>
            <html lang="vi">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s</title>
                <style>
                    body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f4f6f9; margin: 0; padding: 20px; color: #333; }
                    .container { max-width: 580px; margin: 0 auto; background: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 15px rgba(0,0,0,0.08); }
                    .header { background: linear-gradient(135deg, #1e3a8a 0%%, #3b82f6 100%%); color: #ffffff; padding: 28px 24px; text-align: center; }
                    .header h1 { margin: 0; font-size: 24px; font-weight: 700; letter-spacing: 0.5px; }
                    .body { padding: 32px 28px; }
                    .greeting { font-size: 16px; font-weight: 600; color: #1e293b; margin-bottom: 12px; }
                    .text { font-size: 14px; line-height: 1.6; color: #475569; margin-bottom: 24px; }
                    .otp-box { background: #f1f5f9; border: 2px dashed #cbd5e1; border-radius: 10px; padding: 18px; text-align: center; margin: 24px 0; }
                    .otp-code { font-size: 32px; font-weight: 800; letter-spacing: 8px; color: #1d4ed8; font-family: 'Courier New', Courier, monospace; margin: 0; }
                    .warning { font-size: 13px; color: #dc2626; background-color: #fef2f2; border-left: 4px solid #ef4444; padding: 12px 16px; border-radius: 4px; margin-top: 24px; }
                    .footer { background-color: #f8fafc; padding: 20px; text-align: center; font-size: 12px; color: #94a3b8; border-top: 1px solid #e2e8f0; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>DTS Identity</h1>
                    </div>
                    <div class="body">
                        <div class="greeting">Xin chào %s,</div>
                        <div class="text">%s</div>
                        
                        <div class="otp-box">
                            <div class="otp-code">%s</div>
                        </div>
                        
                        <div class="text" style="text-align: center; font-size: 13px; color: #64748b;">
                            Mã OTP này có hiệu lực trong <strong>10 phút</strong>.
                        </div>
                        
                        <div class="warning">
                            <strong>Lưu ý bảo mật:</strong> Không chia sẻ mã OTP này với bất kỳ ai, kể cả nhân viên hỗ trợ DTS.
                        </div>
                    </div>
                    <div class="footer">
                        &copy; 2026 DTS Identity Service. Tất cả quyền được bảo lưu.
                    </div>
                </div>
            </body>
            </html>
            """.formatted(title, name, messageDetail, otp);
    }
}
