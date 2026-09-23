package com.leadquote.service;

import com.leadquote.config.AppProperties;
import com.leadquote.config.CompanyProperties;
import com.leadquote.dto.QuotationGenerateRequest;
import com.leadquote.dto.QuotationItemRequest;
import com.leadquote.dto.RequestChangesRequest;
import com.leadquote.entity.*;
import com.leadquote.exception.ExpiredTokenException;
import com.leadquote.exception.InvalidTokenException;
import com.leadquote.exception.NotFoundException;
import com.leadquote.repository.CustomerResponseRepository;
import com.leadquote.repository.QuotationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QuotationService {

    private final QuotationRepository quotationRepository;
    private final CustomerResponseRepository customerResponseRepository;
    private final PricingRules pricingRules;
    private final PdfService pdfService;
    private final WhatsAppService whatsAppService;
    private final CompanyProperties companyProperties;
    private final AppProperties appProperties;
    private final AuditService auditService;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public Quotation generateQuotation(Lead lead, QuotationGenerateRequest request) {
        Quotation quotation = Quotation.builder()
                .quotationNumber(nextQuotationNumber())
                .lead(lead)
                .customerName(lead.getCustomerName())
                .companyName(lead.getCompanyName())
                .status(QuotationStatus.DRAFT)
                .validUntil(LocalDate.now().plusDays(appProperties.getQuotationValidityDays()))
                .secureToken(generateSecureToken())
                .tokenExpiresAt(LocalDateTime.now().plusDays(appProperties.getQuotationValidityDays()))
                .build();

        List<QuotationItem> items = buildLineItems(lead, request, quotation);
        quotation.setItems(items);

        BigDecimal subtotal = items.stream()
                .map(QuotationItem::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discount = request != null && request.getDiscount() != null
                ? request.getDiscount()
                : subtotal.multiply(pricingRules.getDefaultDiscountPercentage())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal taxPercentage = request != null && request.getTaxPercentage() != null
                ? request.getTaxPercentage()
                : pricingRules.getDefaultTaxPercentage();

        BigDecimal taxableAmount = subtotal.subtract(discount);
        BigDecimal tax = taxableAmount.multiply(taxPercentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal total = taxableAmount.add(tax);

        quotation.setSubtotal(subtotal);
        quotation.setDiscount(discount);
        quotation.setTax(tax);
        quotation.setTotalAmount(total);
        quotation.setStatus(QuotationStatus.GENERATED);

        Quotation saved = quotationRepository.save(quotation);

        String pdfPath = pdfService.generateQuotationPdf(saved);
        saved.setPdfPath(pdfPath);
        saved = quotationRepository.save(saved);

        lead.setStatus(LeadStatus.QUOTATION_GENERATED);
        auditService.log("Quotation", saved.getId(), "QUOTATION_GENERATED",
                "Quotation " + saved.getQuotationNumber() + " generated for lead " + lead.getLeadCode());

        return saved;
    }

    private List<QuotationItem> buildLineItems(Lead lead, QuotationGenerateRequest request, Quotation quotation) {
        if (request != null && request.getItems() != null && !request.getItems().isEmpty()) {
            return request.getItems().stream().map(i -> {
                BigDecimal amount = i.getUnitPrice() != null && i.getQuantity() != null
                        ? i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())).setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                return QuotationItem.builder()
                        .quotation(quotation)
                        .description(i.getDescription())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .amount(amount)
                        .build();
            }).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }

        // Default: derive a single line item from square footage × configurable rate per sqft.
        double sqft = lead.getSquareFeet() != null ? lead.getSquareFeet() : 0d;
        BigDecimal rate = pricingRules.getRatePerSquareFoot();
        BigDecimal amount = rate.multiply(BigDecimal.valueOf(sqft)).setScale(2, RoundingMode.HALF_UP);

        QuotationItem defaultItem = QuotationItem.builder()
                .quotation(quotation)
                .description("Flooring / installation work (" + sqft + " sq. ft.)")
                .quantity(sqft)
                .unitPrice(rate)
                .amount(amount)
                .build();

        if (pricingRules.getDefaultAdditionalCharges().compareTo(BigDecimal.ZERO) > 0) {
            QuotationItem additional = QuotationItem.builder()
                    .quotation(quotation)
                    .description("Additional charges")
                    .quantity(1d)
                    .unitPrice(pricingRules.getDefaultAdditionalCharges())
                    .amount(pricingRules.getDefaultAdditionalCharges())
                    .build();
            return new ArrayList<>(List.of(defaultItem, additional));
        }
        return new ArrayList<>(List.of(defaultItem));
    }

    @Transactional
    public Quotation sendQuotation(Quotation quotation) {
        String quotationPageLink = companyProperties.getPublicBaseUrl() + "/quotation/" + quotation.getSecureToken();
        whatsAppService.sendQuotation(quotation.getLead(), quotation, quotationPageLink);

        quotation.setStatus(QuotationStatus.SENT);
        quotation.setSentAt(LocalDateTime.now());
        quotation.getLead().setStatus(LeadStatus.QUOTATION_SENT);

        auditService.log("Quotation", quotation.getId(), "QUOTATION_SENT",
                "Quotation " + quotation.getQuotationNumber() + " sent via WhatsApp");

        return quotationRepository.save(quotation);
    }

    public Quotation getByPublicToken(String token) {
        Quotation quotation = quotationRepository.findBySecureToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid quotation link"));

        if (quotation.getTokenExpiresAt() != null && quotation.getTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ExpiredTokenException("This quotation link has expired");
        }

        if (quotation.getStatus() == QuotationStatus.SENT) {
            quotation.setStatus(QuotationStatus.VIEWED);
            quotation.setViewedAt(LocalDateTime.now());
            quotation.getLead().setStatus(LeadStatus.QUOTATION_VIEWED);
            quotationRepository.save(quotation);
        }

        return quotation;
    }

    @Transactional
    public Quotation acceptQuotation(String token) {
        Quotation quotation = getValidQuotationForToken(token);

        quotation.setStatus(QuotationStatus.ACCEPTED);
        quotation.setRespondedAt(LocalDateTime.now());
        quotation.getLead().setStatus(LeadStatus.ACCEPTED);

        customerResponseRepository.save(CustomerResponse.builder()
                .quotation(quotation)
                .responseType(QuotationStatus.ACCEPTED)
                .build());

        Quotation saved = quotationRepository.save(quotation);

        whatsAppService.sendAcceptanceConfirmation(saved.getLead());
        whatsAppService.sendContactDetails(saved.getLead());

        auditService.log("Quotation", saved.getId(), "QUOTATION_ACCEPTED",
                "Customer accepted quotation " + saved.getQuotationNumber());

        return saved;
    }

    @Transactional
    public Quotation requestChanges(String token, RequestChangesRequest request) {
        Quotation quotation = getValidQuotationForToken(token);

        quotation.setStatus(QuotationStatus.CHANGES_REQUESTED);
        quotation.setRespondedAt(LocalDateTime.now());
        quotation.getLead().setStatus(LeadStatus.CHANGES_REQUESTED);

        customerResponseRepository.save(CustomerResponse.builder()
                .quotation(quotation)
                .responseType(QuotationStatus.CHANGES_REQUESTED)
                .changeRequestNotes(request.getNotes())
                .build());

        Quotation saved = quotationRepository.save(quotation);

        auditService.log("Quotation", saved.getId(), "CHANGES_REQUESTED",
                "Customer requested changes: " + request.getNotes());

        // NOTE: wire this into your notification-to-employee channel (email/Slack/in-app)
        return saved;
    }

    private Quotation getValidQuotationForToken(String token) {
        Quotation quotation = quotationRepository.findBySecureToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid quotation link"));

        if (quotation.getTokenExpiresAt() != null && quotation.getTokenExpiresAt().isBefore(LocalDateTime.now())) {
            quotation.setStatus(QuotationStatus.EXPIRED);
            quotationRepository.save(quotation);
            throw new ExpiredTokenException("This quotation has expired");
        }
        if (quotation.getStatus() == QuotationStatus.ACCEPTED || quotation.getStatus() == QuotationStatus.REJECTED) {
            throw new IllegalStateException("This quotation has already been responded to");
        }
        return quotation;
    }

    public Quotation getById(Long id) {
        return quotationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Quotation not found: " + id));
    }

    private synchronized String nextQuotationNumber() {
        String prefix = "QT-" + Year.now().getValue() + "-";
        long count = quotationRepository.countByQuotationNumberStartingWith(prefix) + 1;
        return prefix + String.format("%04d", count);
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}