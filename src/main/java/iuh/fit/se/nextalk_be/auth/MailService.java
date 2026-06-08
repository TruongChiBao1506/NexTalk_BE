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

    public void sendVerificationEmail(String toEmail, String verificationLink) {
        String subject = "NexTalk - Verify Your Email Account";
        String htmlContent = "<h3>Welcome to NexTalk!</h3>"
                + "<p>Please click the link below to verify your email address and activate your account:</p>"
                + "<p><a href=\"" + verificationLink + "\" style=\"display: inline-block; padding: 10px 20px; color: white; background-color: #007bff; text-decoration: none; border-radius: 5px;\">Verify Email</a></p>"
                + "<p>This link will expire in 24 hours.</p>";

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(toEmail);
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
