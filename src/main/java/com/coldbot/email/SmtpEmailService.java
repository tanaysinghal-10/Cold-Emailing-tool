package com.coldbot.email;

import com.coldbot.common.exception.EmailSendingException;
import com.coldbot.common.exception.RateLimitExceededException;
import com.coldbot.job.entity.EmailRateLimit;
import com.coldbot.job.entity.User;
import com.coldbot.job.repository.EmailRateLimitRepository;
import com.coldbot.job.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final EmailRateLimitRepository rateLimitRepository;
    private final UserRepository userRepository;

    @Value("${email.from.address:}")
    private String fromAddress;

    @Value("${email.from.name:Job Applicant}")
    private String fromName;

    @Value("${email.daily-limit:10}")
    private int dailyLimit;

    @Override
    @Transactional
    public void sendEmail(String to, String subject, String body,
                          byte[] resumeAttachment, String resumeFilename) {
        log.info("Sending email to: {} | Subject: {}", to, subject);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            if (resumeAttachment != null && resumeAttachment.length > 0) {
                String filename = (resumeFilename != null && !resumeFilename.isBlank()) ? resumeFilename : "resume.pdf";
                helper.addAttachment(filename, new ByteArrayResource(resumeAttachment));
                log.info("Attached resume: {} ({} bytes)", filename, resumeAttachment.length);
            }
            mailSender.send(message);
            log.info("Email sent successfully to: {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage(), e);
            throw new EmailSendingException("Failed to send email: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending email to {}: {}", to, e.getMessage(), e);
            throw new EmailSendingException("Email sending failed unexpectedly: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean canSendEmail(Long userId) {
        return getRemainingEmailsToday(userId) > 0;
    }

    @Override
    public int getRemainingEmailsToday(Long userId) {
        LocalDate today = LocalDate.now();
        EmailRateLimit rateLimit = rateLimitRepository.findByUserIdAndRateDate(userId, today).orElse(null);
        if (rateLimit == null) return dailyLimit;
        return Math.max(0, rateLimit.getDailyLimit() - rateLimit.getEmailsSent());
    }

    @Transactional
    public void recordEmailSent(Long userId) {
        LocalDate today = LocalDate.now();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        EmailRateLimit rateLimit = rateLimitRepository.findByUserIdAndRateDate(userId, today)
                .orElseGet(() -> EmailRateLimit.builder()
                        .user(user).rateDate(today).emailsSent(0).dailyLimit(dailyLimit).build());
        if (rateLimit.getEmailsSent() >= rateLimit.getDailyLimit()) {
            throw new RateLimitExceededException("Daily email limit of " + rateLimit.getDailyLimit() + " reached.");
        }
        rateLimit.setEmailsSent(rateLimit.getEmailsSent() + 1);
        rateLimitRepository.save(rateLimit);
        log.info("Email count for user {}: {}/{}", userId, rateLimit.getEmailsSent(), rateLimit.getDailyLimit());
    }
}
