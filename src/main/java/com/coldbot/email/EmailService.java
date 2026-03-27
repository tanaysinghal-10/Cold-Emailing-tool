package com.coldbot.email;

public interface EmailService {
    void sendEmail(String to, String subject, String body, byte[] resumeAttachment, String resumeFilename);
    boolean canSendEmail(Long userId);
    int getRemainingEmailsToday(Long userId);
}
