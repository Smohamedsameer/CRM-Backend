package com.leadquote.controller;

import com.leadquote.entity.WhatsAppMessage;
import com.leadquote.exception.NotFoundException;
import com.leadquote.repository.LeadRepository;
import com.leadquote.repository.WhatsAppMessageRepository;
import com.leadquote.service.AuditService;
import com.leadquote.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Employee-facing view of WhatsApp message history + manual retry for failed sends. */
@RestController
@RequestMapping("/api/whatsapp-messages")
@RequiredArgsConstructor
public class WhatsAppMessageController {

    private final WhatsAppMessageRepository whatsAppMessageRepository;
    private final WhatsAppService whatsAppService;
    private final LeadRepository leadRepository;
    private final AuditService auditService;

    @PostMapping("/{id}/retry")
    public WhatsAppMessage retry(@PathVariable Long id) {
        WhatsAppMessage message = whatsAppMessageRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Message not found: " + id));
        return whatsAppService.retry(message);
    }

    /** Clears all WhatsApp message history logged against a lead. Does not affect messages on WhatsApp itself. */
    @DeleteMapping("/lead/{leadId}")
    @Transactional
    public void clearHistory(@PathVariable Long leadId) {
        if (!leadRepository.existsById(leadId)) {
            throw new NotFoundException("Lead not found: " + leadId);
        }
        whatsAppMessageRepository.deleteByLeadId(leadId);
        auditService.log("Lead", leadId, "WHATSAPP_HISTORY_CLEARED", "WhatsApp message history cleared for lead " + leadId);
    }
}
