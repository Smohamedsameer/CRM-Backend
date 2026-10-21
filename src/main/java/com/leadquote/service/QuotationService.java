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
import com.leadquote.repository.QuotationFormDataRepository;
import com.leadquote.repository.QuotationPdfFileRepository;
import com.leadquote.repository.QuotationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

@Slf4j
@Service
@RequiredArgsConstructor
public class QuotationService {

    /** Largest PDF accepted when an employee uploads an edited quotation. */
    private static final int MAX_PDF_BYTES = 25 * 1024 * 1024;

    private final QuotationRepository quotationRepository;
    private final QuotationPdfFileRepository quotationPdfFileRepository;
    private final QuotationFormDataRepository quotationFormDataRepository;
    private final CustomerResponseRepository customerResponseRepository;
    private final SettingsService settingsService;
    private final PdfService pdfService;
    private final WhatsAppService whatsAppService;
    private final CompanyProperties companyProperties;
    private final AppProperties appProperties;
    private final AuditService auditService;
    private final NotificationService notificationService;

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
                : subtotal.multiply(settingsService.getDefaultDiscountPercentage())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal taxPercentage = request != null && request.getTaxPercentage() != null
                ? request.getTaxPercentage()
                : settingsService.getDefaultTaxPercentage();

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
        BigDecimal rate = settingsService.getRatePerSquareFoot();
        BigDecimal amount = rate.multiply(BigDecimal.valueOf(sqft)).setScale(2, RoundingMode.HALF_UP);

        QuotationItem defaultItem = QuotationItem.builder()
                .quotation(quotation)
                .description("Flooring / installation work (" + sqft + " sq. ft.)")
                .quantity(sqft)
                .unitPrice(rate)
                .amount(amount)
                .build();

        if (settingsService.getDefaultAdditionalCharges().compareTo(BigDecimal.ZERO) > 0) {
            QuotationItem additional = QuotationItem.builder()
                    .quotation(quotation)
                    .description("Additional charges")
                    .quantity(1d)
                    .unitPrice(settingsService.getDefaultAdditionalCharges())
                    .amount(settingsService.getDefaultAdditionalCharges())
                    .build();
            return new ArrayList<>(List.of(defaultItem, additional));
        }
        return new ArrayList<>(List.of(defaultItem));
    }

    @Transactional
    public Quotation sendQuotation(Quotation quotation) {
        if (quotation.getStatus() == QuotationStatus.ACCEPTED) {
            throw new IllegalArgumentException("The customer has already accepted this quotation, so it can't be sent again.");
        }
        // Re-sending after the link expired (e.g. a revised quotation): give the customer a fresh validity period.
        if (quotation.getTokenExpiresAt() != null && quotation.getTokenExpiresAt().isBefore(LocalDateTime.now())) {
            quotation.setTokenExpiresAt(LocalDateTime.now().plusDays(appProperties.getQuotationValidityDays()));
            quotation.setValidUntil(LocalDate.now().plusDays(appProperties.getQuotationValidityDays()));
        }
        String quotationPageLink = companyProperties.getPublicBaseUrl() + "/quotation/" + quotation.getSecureToken();
        // Make sure the PDF really exists on disk before WhatsApp tries to download it.
        ensurePdfFile(quotation);
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

    /**
     * Token check for the PDF download only. Deliberately does NOT mark the quotation as VIEWED:
     * WhatsApp's own servers download this file when the message is sent, which would otherwise
     * flip the status to "Viewed" before the customer has even opened it.
     */
    public Quotation getByPublicTokenForPdf(String token) {
        Quotation quotation = quotationRepository.findBySecureToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid quotation link"));
        if (quotation.getTokenExpiresAt() != null && quotation.getTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ExpiredTokenException("This quotation link has expired");
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

        // order_util already includes the admin phone AND the (fixed) company email in one
        // message - do not also call sendContactDetails(), that would fire the Graph API twice
        // for the same template.
        whatsAppService.sendAcceptanceConfirmation(saved.getLead());

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

        notificationService.create(
                NotificationType.CHANGES_REQUESTED,
                "Changes requested",
                saved.getLead().getCustomerName() + " requested changes to quotation " + saved.getQuotationNumber()
                        + (request.getNotes() != null && !request.getNotes().isBlank() ? ": " + request.getNotes() : ""),
                "Lead", saved.getLead().getId(), "/leads/" + saved.getLead().getId());

        return saved;
    }

    /**
     * The customer tapped "Call to confirm order" on the quotation page. The lead is marked CALLED_TO_ORDER so the
     * dashboard shows it, and the team gets a notification. Never downgrades a lead that is already an order.
     */
    @Transactional
    public Quotation markCalledToOrder(String token) {
        Quotation quotation = getByPublicTokenForPdf(token); // token + expiry check, does not change the quotation status
        Lead lead = quotation.getLead();
        if (lead.getStatus() == LeadStatus.ORDER_CONFIRMED || lead.getStatus() == LeadStatus.CALLED_TO_ORDER) {
            return quotation;
        }
        lead.setStatus(LeadStatus.CALLED_TO_ORDER);
        Quotation saved = quotationRepository.save(quotation);

        auditService.log("Quotation", saved.getId(), "CALLED_TO_ORDER",
                "Customer tapped Call to confirm order on quotation " + saved.getQuotationNumber());
        notificationService.create(
                NotificationType.CALLED_TO_ORDER,
                "Customer is calling to confirm the order",
                lead.getCustomerName() + " tapped \"Call to confirm order\" on quotation " + saved.getQuotationNumber(),
                "Lead", lead.getId(), "/leads/" + lead.getId());
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

    /**
     * Returns the quotation's PDF file, regenerating it if it is missing. Railway (and most PaaS
     * hosts) wipe the local disk on every redeploy, so an old quotation's PDF may no longer exist.
     */
    @Transactional
    public java.io.File ensurePdfFile(Quotation quotation) {
        String path = quotation.getPdfPath();
        if (path != null) {
            java.io.File f = new java.io.File(path);
            if (f.exists() && f.length() > 0) return f;
        }
        // The disk was wiped (redeploy): bring back the exact PDF that was uploaded, if we kept a copy.
        java.io.File restored = restoreFromDatabase(quotation);
        if (restored != null) return restored;
        String regenerated = pdfService.generateQuotationPdf(quotation);
        quotation.setPdfPath(regenerated);
        quotationRepository.save(quotation);
        return new java.io.File(regenerated);
    }

    /** Saves the quotation-builder details (JSON) so the Word version can be built later. */
    @Transactional
    public void saveFormData(Long id, String json) {
        Quotation quotation = getById(id);
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("No quotation details were sent.");
        }
        if (json.length() > 30_000_000) {
            throw new IllegalArgumentException("The quotation details are too large (maximum about 30 MB, usually because of big pictures).");
        }
        quotationFormDataRepository.deleteByQuotationId(quotation.getId());
        quotationFormDataRepository.save(QuotationFormData.builder().quotationId(quotation.getId()).json(json).build());
    }

    /** The saved quotation-builder details, or null if none were saved (quotation made before this feature). */
    @Transactional(readOnly = true)
    public String getFormData(Long id) {
        return quotationFormDataRepository.findByQuotationId(id).map(QuotationFormData::getJson).orElse(null);
    }

    private java.io.File restoreFromDatabase(Quotation quotation) {
        java.util.Optional<QuotationPdfFile> copy = quotationPdfFileRepository.findByQuotationId(quotation.getId());
        if (copy.isEmpty()) return null;
        try {
            java.io.File dir = new java.io.File(appProperties.getPdfStorageDir());
            if (!dir.exists()) dir.mkdirs();
            java.io.File file = new java.io.File(dir, quotation.getQuotationNumber().replace("/", "-") + ".pdf");
            java.nio.file.Files.write(file.toPath(), copy.get().getData());
            quotation.setPdfPath(file.getPath());
            quotationRepository.save(quotation);
            return file;
        } catch (java.io.IOException ex) {
            log.warn("Could not restore the stored PDF of {}: {}", quotation.getQuotationNumber(), ex.getMessage());
            return null;
        }
    }

    /** Replaces the stored PDF: the 8-page PEB quotation generated in the browser, or the PDF an employee edited in Word and uploaded. */
    @Transactional
    public Quotation replacePdf(Long id, byte[] bytes) {
        Quotation quotation = getById(id);
        boolean looksLikePdf = bytes != null && bytes.length > 1000
                && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
        if (!looksLikePdf) {
            throw new com.leadquote.exception.PdfGenerationException("The uploaded file is not a valid PDF", null);
        }
        if (bytes.length > MAX_PDF_BYTES) {
            throw new IllegalArgumentException("The PDF is too large (maximum 25 MB).");
        }
        try {
            java.io.File dir = new java.io.File(appProperties.getPdfStorageDir());
            if (!dir.exists()) dir.mkdirs();
            java.io.File file = new java.io.File(dir, quotation.getQuotationNumber().replace("/", "-") + ".pdf");
            java.nio.file.Files.write(file.toPath(), bytes);
            quotation.setPdfPath(file.getPath());
            // Keep a copy in the database too, so a redeploy cannot bring back the old auto-generated PDF.
            quotationPdfFileRepository.deleteByQuotationId(quotation.getId());
            quotationPdfFileRepository.save(QuotationPdfFile.builder().quotationId(quotation.getId()).data(bytes).build());
            auditService.log("Quotation", quotation.getId(), "QUOTATION_PDF_REPLACED",
                    "PEB quotation PDF saved for " + quotation.getQuotationNumber());
            return quotationRepository.save(quotation);
        } catch (java.io.IOException ex) {
            throw new com.leadquote.exception.PdfGenerationException("Could not save the quotation PDF", ex);
        }
    }

    public Quotation getById(Long id) {
        return quotationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Quotation not found: " + id));
    }

    /** Format: SE-1001-2026 (company prefix - running sequence starting at 1001 - year issued). */
    private synchronized String nextQuotationNumber() {
        String prefix = "SE-";
        // Highest number in use + 1. (Counting rows would hand out a duplicate number once a lead has been deleted.)
        long highest = 1000;
        for (String number : quotationRepository.findAllNumbersStartingWith(prefix)) {
            String[] parts = number.split("-"); // SE-1001-2026
            if (parts.length >= 2) {
                try {
                    highest = Math.max(highest, Long.parseLong(parts[1]));
                } catch (NumberFormatException ignored) {
                    // not one of our numbers
                }
            }
        }
        return prefix + (highest + 1) + "-" + Year.now().getValue();
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}