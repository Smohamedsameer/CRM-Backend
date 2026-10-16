package com.leadquote.service;

import com.leadquote.config.AppProperties;
import com.leadquote.config.CompanyProperties;
import com.leadquote.dto.LeadCreateRequest;
import com.leadquote.entity.Lead;
import com.leadquote.entity.LeadStatus;
import com.leadquote.entity.Order;
import com.leadquote.entity.Quotation;
import com.leadquote.exception.ExpiredTokenException;
import com.leadquote.exception.InvalidTokenException;
import com.leadquote.exception.NotFoundException;
import com.leadquote.repository.LeadRepository;
import com.leadquote.repository.NotificationRepository;
import com.leadquote.repository.OrderRepository;
import com.leadquote.repository.QuotationPdfFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.File;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class LeadService {

    private final LeadRepository leadRepository;
    private final WhatsAppService whatsAppService;
    private final CompanyProperties companyProperties;
    private final AppProperties appProperties;
    private final AuditService auditService;
    private final OrderRepository orderRepository;
    private final NotificationRepository notificationRepository;
    private final QuotationPdfFileRepository quotationPdfFileRepository;

    private final SecureRandom secureRandom = new SecureRandom();
    // Simple in-memory counter fallback; for multi-instance deployments back this with a DB sequence.
    private final AtomicLong sequence = new AtomicLong(1000);

    @Transactional
    public Lead createLead(LeadCreateRequest request, String createdByEmail) {
        Lead lead = Lead.builder()
                .leadCode(generateLeadCode())
                .customerName(request.getCustomerName())
                .companyName(request.getCompanyName())
                .address(request.getAddress())
                .projectName(request.getProjectName())
                .squareFeet(request.getSquareFeet())
                .estimatedAmount(request.getEstimatedAmount())
                .phone(request.getPhone())
                .email(request.getEmail())
                .source(request.getSource())
                .status(LeadStatus.NEW)
                .enquiryToken(generateToken())
                .enquiryTokenExpiresAt(LocalDateTime.now().plusHours(appProperties.getEnquiryTokenValidityHours()))
                .build();

        Lead saved = leadRepository.save(lead);
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

    /**
     * Permanently deletes a lead and everything that belongs to it: the enquiry, quotations (with their items
     * and customer responses), the order, the WhatsApp / chat history, the bell notifications and the stored PDFs.
     * The audit log keeps a "LEAD_DELETED" entry.
     */
    @Transactional
    public void deleteLead(Long id, String deletedByEmail) {
        Lead lead = getById(id);
        String code = lead.getLeadCode();
        String name = lead.getCustomerName();

        List<String> pdfPaths = new ArrayList<>();
        for (Quotation quotation : lead.getQuotations()) {
            if (quotation.getPdfPath() != null) pdfPaths.add(quotation.getPdfPath());
            quotationPdfFileRepository.deleteByQuotationId(quotation.getId());
        }

        // An order points at a quotation, so it has to go before the quotations do.
        Order order = lead.getOrder();
        if (order != null) {
            lead.setOrder(null);
            orderRepository.delete(order);
            orderRepository.flush();
        }

        notificationRepository.deleteByReference("Lead", id);

        // Cascades to enquiry, quotations (+ items, customer responses) and WhatsApp messages.
        leadRepository.delete(lead);

        auditService.log("Lead", id, "LEAD_DELETED", "Lead " + code + " (" + name + ") deleted by " + deletedByEmail);

        // Remove the PDF files from disk only once the database delete has really been committed.
        if (!pdfPaths.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (String path : pdfPaths) {
                        try {
                            new File(path).delete();
                        } catch (RuntimeException ignored) {
                            // a leftover file is harmless
                        }
                    }
                }
            });
        }
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
