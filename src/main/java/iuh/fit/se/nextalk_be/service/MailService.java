package iuh.fit.se.nextalk_be.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

public interface MailService {
    public void sendPasswordResetEmail(String toEmail, String resetLink);
    public void sendVerificationEmail(String toEmail, String verificationLink);
}
