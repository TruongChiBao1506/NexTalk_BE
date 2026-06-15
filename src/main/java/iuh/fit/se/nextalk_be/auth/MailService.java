package iuh.fit.se.nextalk_be.auth;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    @org.springframework.beans.factory.annotation.Value("${spring.mail.username:}")
    private String senderEmail;

    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        String subject = "NexTalk - Khôi phục mật khẩu";
        String htmlContent = "<div style=\"font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 30px; background-color: #f4f7fa; border-radius: 16px;\">"
                + "<div style=\"text-align: center; margin-bottom: 25px;\">"
                + "<h1 style=\"color: #4f46e5; margin: 0; font-size: 32px; letter-spacing: -0.5px;\">NexTalk</h1>"
                + "</div>"
                + "<div style=\"background-color: white; padding: 40px; border-radius: 16px; box-shadow: 0 10px 25px rgba(0,0,0,0.05); text-align: center;\">"
                + "<h2 style=\"color: #1e293b; margin-top: 0; margin-bottom: 15px; font-size: 22px;\">Khôi phục mật khẩu</h2>"
                + "<p style=\"color: #475569; font-size: 16px; line-height: 1.6; margin-bottom: 30px;\">Bạn vừa yêu cầu đặt lại mật khẩu cho tài khoản của mình. Hãy nhấn vào nút bên dưới để tiến hành thiết lập mật khẩu mới.</p>"
                + "<a href=\"" + resetLink + "\" style=\"display: inline-block; padding: 14px 32px; color: white; background-color: #4f46e5; text-decoration: none; border-radius: 10px; font-weight: 600; font-size: 16px; box-shadow: 0 4px 12px rgba(79, 70, 229, 0.25);\">Đặt lại mật khẩu</a>"
                + "<p style=\"color: #94a3b8; font-size: 14px; margin-top: 30px; margin-bottom: 0;\">Nếu bạn không yêu cầu điều này, xin hãy bỏ qua email.<br>Liên kết này sẽ hết hạn trong vòng 15 phút.</p>"
                + "</div>"
                + "<p style=\"color: #94a3b8; font-size: 13px; text-align: center; margin-top: 25px;\">&copy; " + java.time.Year.now().getValue() + " NexTalk Team.</p>"
                + "</div>";

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(toEmail);
            if (senderEmail != null && !senderEmail.isEmpty()) {
                helper.setFrom(senderEmail, "NexTalk");
            }
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Password reset email sent successfully to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}. Fallback: logging the reset link: {}",
                    toEmail, resetLink, e);
            System.out.println("=================================================");
            System.out.println("PASSWORD RESET LINK FOR " + toEmail + ":");
            System.out.println(resetLink);
            System.out.println("=================================================");
        }
    }

    public void sendVerificationEmail(String toEmail, String verificationLink) {
        String subject = "NexTalk - Xác thực tài khoản của bạn";
        String htmlContent = "<div style=\"font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 30px; background-color: #f4f7fa; border-radius: 16px;\">"
                + "<div style=\"text-align: center; margin-bottom: 25px;\">"
                + "<h1 style=\"color: #4f46e5; margin: 0; font-size: 32px; letter-spacing: -0.5px;\">NexTalk</h1>"
                + "</div>"
                + "<div style=\"background-color: white; padding: 40px; border-radius: 16px; box-shadow: 0 10px 25px rgba(0,0,0,0.05); text-align: center;\">"
                + "<h2 style=\"color: #1e293b; margin-top: 0; margin-bottom: 15px; font-size: 22px;\">Xác thực tài khoản</h2>"
                + "<p style=\"color: #475569; font-size: 16px; line-height: 1.6; margin-bottom: 30px;\">Chào mừng bạn đến với <strong>NexTalk</strong>! Chúng tôi rất vui được đón tiếp bạn. Vui lòng nhấn vào nút bên dưới để xác thực địa chỉ email và hoàn tất việc đăng ký.</p>"
                + "<a href=\"" + verificationLink + "\" style=\"display: inline-block; padding: 14px 32px; color: white; background-color: #4f46e5; text-decoration: none; border-radius: 10px; font-weight: 600; font-size: 16px; box-shadow: 0 4px 12px rgba(79, 70, 229, 0.25);\">Xác thực Email</a>"
                + "<p style=\"color: #94a3b8; font-size: 14px; margin-top: 30px; margin-bottom: 0;\">Liên kết này sẽ hết hạn trong vòng 24 giờ.</p>"
                + "</div>"
                + "<p style=\"color: #94a3b8; font-size: 13px; text-align: center; margin-top: 25px;\">&copy; " + java.time.Year.now().getValue() + " NexTalk Team.</p>"
                + "</div>";

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(toEmail);
            if (senderEmail != null && !senderEmail.isEmpty()) {
                helper.setFrom(senderEmail, "NexTalk");
            }
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("Verification email sent successfully to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}. Fallback: logging the verification link: {}",
                    toEmail, verificationLink, e);
            // Fallback for developers who haven't set up the mail server
            System.out.println("=================================================");
            System.out.println("VERIFICATION LINK FOR " + toEmail + ":");
            System.out.println(verificationLink);
            System.out.println("=================================================");
        }
    }
}
