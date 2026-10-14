package com.leadquote.service;

import com.leadquote.config.CompanyProperties;
import com.leadquote.config.WhatsAppProperties;
import com.leadquote.entity.Lead;
import com.leadquote.entity.LeadStatus;
import com.leadquote.entity.MessageDeliveryStatus;
import com.leadquote.entity.WhatsAppMessageType;
import com.leadquote.repository.EnquiryRepository;
import com.leadquote.repository.LeadRepository;
import com.leadquote.repository.WhatsAppMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

/**
 * Sends the "remind_util" WhatsApp template to a customer who received the welcome message / enquiry form
 * link but has not submitted the form by the next day.
 *
 * Rules:
 *  - lead is still WELCOME_SENT or FORM_SENT (i.e. no enquiry submitted yet) and has no Enquiry row;
 *  - the lead is at least reminder-delay-hours old (default 24) and at most reminder-max-age-hours old
 *    (default 72), so turning this on never messages old leads;
 *  - only ONE reminder is ever delivered per lead; a failed attempt is retried on the next run, up to 3 tries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnquiryReminderService {

    private static final int MAX_FAILED_ATTEMPTS = 3;

    private final LeadRepository leadRepository;
    private final EnquiryRepository enquiryRepository;
    private final WhatsAppMessageRepository whatsAppMessageRepository;
    private final WhatsAppService whatsAppService;
    private final WhatsAppProperties whatsAppProperties;
    private final CompanyProperties companyProperties;
    private final AuditService auditService;

    /** Runs every 15 minutes (first run 2 minutes after start-up). */
    @Scheduled(initialDelay = 120_000, fixedDelay = 900_000)
    public void sendDueReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.minusHours(whatsAppProperties.getReminderMaxAgeHours());
        LocalDateTime to = now.minusHours(whatsAppProperties.getReminderDelayHours());

        List<Lead> candidates = leadRepository.findByStatusInAndCreatedAtBetween(
                EnumSet.of(LeadStatus.WELCOME_SENT, LeadStatus.FORM_SENT), from, to);

        for (Lead lead : candidates) {
            try {
                remindIfNeeded(lead, now);
            } catch (Exception ex) {
                // One bad lead (e.g. WhatsApp rejected the number) must not stop the others.
                log.warn("Enquiry reminder failed for lead {}: {}", lead.getLeadCode(), ex.getMessage());
            }
        }
    }

    private void remindIfNeeded(Lead lead, LocalDateTime now) {
        if (enquiryRepository.findByLeadId(lead.getId()).isPresent()) return;          // already filled
        if (lead.getEnquiryToken() == null) return;
        if (lead.getEnquiryTokenExpiresAt() != null && lead.getEnquiryTokenExpiresAt().isBefore(now)) return;

        Long id = lead.getId();
        if (whatsAppMessageRepository.countByLeadIdAndTypeAndDeliveryStatusNot(
                id, WhatsAppMessageType.ENQUIRY_FORM, MessageDeliveryStatus.FAILED) > 0) return;   // already reminded
        if (whatsAppMessageRepository.countByLeadIdAndType(id, WhatsAppMessageType.ENQUIRY_FORM)
                >= MAX_FAILED_ATTEMPTS) return;                                                      // gave up

        String link = companyProperties.getPublicBaseUrl() + "/enquiry/" + lead.getEnquiryToken();
        whatsAppService.sendEnquiryReminder(lead, link);
        auditService.log("Lead", id, "ENQUIRY_REMINDER_SENT", "Reminder sent to " + lead.getPhone());
    }
}
