package com.leadquote.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Optional email notifications (e.g. notifying an employee when a customer requests changes,
 * or emailing the quotation PDF as a backup channel to WhatsApp).
 * Credentials come from EMAIL_USERNAME / EMAIL_PASSWORD env vars via spring.mail.* properties.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendPlainText(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception ex) {
            log.error("Failed to send email to {}: {}", to, ex.getMessage());
            // Non-fatal: email is a secondary channel, never blocks the core workflow.
        }
    }
}
