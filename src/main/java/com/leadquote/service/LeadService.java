package com.leadquote.service;

import com.leadquote.config.AppProperties;
import com.leadquote.config.CompanyProperties;
import com.leadquote.dto.LeadCreateRequest;
import com.leadquote.entity.Lead;
import com.leadquote.entity.LeadCoverImage;
import com.leadquote.repository.LeadCoverImageRepository;
import com.leadquote.entity.LeadStatus;
import com.leadquote.exception.ExpiredTokenException;
import com.leadquote.exception.InvalidTokenException;
import com.leadquote.exception.NotFoundException;
import com.leadquote.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leadRepository;
    private final LeadCoverImageRepository coverImageRepository;
    private final WhatsAppService whatsAppService;
    private final CompanyProperties companyProperties;
    private final AppProperties appProperties;
    private final AuditService auditService;

    private final SecureRandom secureRandom = new SecureRandom();
    // Simple in-memory counter fallback; for multi-instance deployments back this with a DB sequence.
    private final AtomicLong sequence = new AtomicLong(1000);

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Stores the cover photo sent as a data URL; silently ignores anything that is not a valid image. */
    private void saveCoverImage(Long leadId, String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) return;
        try {
            int comma = dataUrl.indexOf(',');
            if (!dataUrl.startsWith("data:image/") || comma < 0) return;
            String contentType = dataUrl.substring(5, dataUrl.indexOf(';') > 0 ? dataUrl.indexOf(';') : comma);
            byte[] bytes = Base64.getDecoder().decode(dataUrl.substring(comma + 1).trim());
            if (bytes.length == 0 || bytes.length > 8 * 1024 * 1024) return;
            coverImageRepository.save(LeadCoverImage.builder().leadId(leadId).contentType(contentType).data(bytes).build());
        } catch (IllegalArgumentException ex) {
            // malformed base64 - the lead is still created without a cover image
        }
    }

    @Transactional
    public Lead createLead(LeadCreateRequest request, String createdByEmail) {
        Lead lead = Lead.builder()
                .leadCode(generateLeadCode())
                .customerName(request.getCustomerName())
                .companyName(request.getCompanyName())
                .squareFeet(request.getSquareFeet())
                .estimatedAmount(request.getEstimatedAmount())
                .phone(request.getPhone())
                .email(request.getEmail())
                .address(blankToNull(request.getAddress()))
                .pincode(blankToNull(request.getPincode()))
                .gstNumber(blankToNull(request.getGstNumber()) == null ? null : request.getGstNumber().trim().toUpperCase())
                .projectName(blankToNull(request.getProjectName()))
                .quotationDate(request.getQuotationDate())
                .source(request.getSource())
                .status(LeadStatus.NEW)
                .enquiryToken(generateToken())
                .enquiryTokenExpiresAt(LocalDateTime.now().plusHours(appProperties.getEnquiryTokenValidityHours()))
                .build();

        Lead saved = leadRepository.save(lead);
        saveCoverImage(saved.getId(), request.getCoverImage());
        auditService.log("Lead", saved.getId(), "LEAD_CREATED", "Lead " + saved.getLeadCode() + " created by " + createdByEmail);

        triggerWelcomeWorkflow(saved);

        return saved;
    }

    /** Called right after creation, and also exposed so an employee can manually retry if WhatsApp failed. */
    @Transactional
    public Lead triggerWelcomeWorkflow(Lead lead) {
        String enquiryLink = companyProperties.getPublicBaseUrl() + "/enquiry/" + lead.getEnquiryToken();
        try {
            whatsAppService.sendWelcomeMessage(lead, enquiryLink);
            lead.setStatus(LeadStatus.WELCOME_SENT);
            auditService.log("Lead", lead.getId(), "WELCOME_MESSAGE_SENT", "Welcome message sent to " + lead.getPhone());
        } catch (Exception ex) {
            // Lead is never lost: it just stays at NEW / current status so the employee can retry from the dashboard.
            auditService.log("Lead", lead.getId(), "WELCOME_MESSAGE_FAILED", ex.getMessage());
        }
        return leadRepository.save(lead);
    }

    public Lead getByEnquiryToken(String token) {
        Lead lead = leadRepository.findByEnquiryToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid enquiry link"));

        if (lead.getEnquiryTokenExpiresAt() != null && lead.getEnquiryTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ExpiredTokenException("This enquiry link has expired. Please contact us for a new link.");
        }
        return lead;
    }

    public Lead getById(Long id) {
        return leadRepository.findById(id).orElseThrow(() -> new NotFoundException("Lead not found: " + id));
    }

    public Lead getByLeadCode(String leadCode) {
        return leadRepository.findByLeadCode(leadCode).orElseThrow(() -> new NotFoundException("Lead not found: " + leadCode));
    }

    public Page<Lead> search(LeadStatus status, String search, Pageable pageable) {
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.toLowerCase();
        return leadRepository.search(status, normalizedSearch, pageable);
    }

    @Transactional
    public Lead updateStatus(Long id, LeadStatus status) {
        Lead lead = getById(id);
        lead.setStatus(status);
        auditService.log("Lead", lead.getId(), "STATUS_UPDATED", "Status changed to " + status);
        return leadRepository.save(lead);
    }

    private String generateLeadCode() {
        String code;
        do {
            code = "LEAD-" + sequence.incrementAndGet();
        } while (leadRepository.existsByLeadCode(code));
        return code;
    }

    private String generateToken() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
