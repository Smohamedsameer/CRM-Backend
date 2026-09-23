package com.leadquote.controller;

import com.leadquote.dto.EnquirySubmitRequest;
import com.leadquote.dto.RequestChangesRequest;
import com.leadquote.entity.Enquiry;
import com.leadquote.entity.Lead;
import com.leadquote.entity.Quotation;
import com.leadquote.service.EnquiryService;
import com.leadquote.service.LeadService;
import com.leadquote.service.QuotationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Everything here is reachable WITHOUT an employee JWT (see SecurityConfig: /api/public/** is permitAll),
 * but every operation is gated by a secure, expiring, single-purpose token - never by guessable IDs.
 * No sensitive database fields beyond what the customer needs are ever returned.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final LeadService leadService;
    private final EnquiryService enquiryService;
    private final QuotationService quotationService;

    // ---- Enquiry form --------------------------------------------------

    @GetMapping("/enquiry/{token}")
    public Map<String, Object> getEnquiryFormPrefill(@PathVariable String token) {
        Lead lead = leadService.getByEnquiryToken(token);
        return Map.of(
                "customerName", lead.getCustomerName(),
                "companyName", lead.getCompanyName() == null ? "" : lead.getCompanyName(),
                "phone", lead.getPhone(),
                "email", lead.getEmail() == null ? "" : lead.getEmail(),
                "squareFeet", lead.getSquareFeet() == null ? "" : lead.getSquareFeet()
        );
    }

    @PostMapping("/enquiry")
    public Enquiry submitEnquiry(@Valid @RequestBody EnquirySubmitRequest request) {
        return enquiryService.submit(request);
    }

    // ---- Quotation accept / request-changes -----------------------------

    @GetMapping("/quotations/{token}")
    public Map<String, Object> viewQuotation(@PathVariable String token) {
        Quotation q = quotationService.getByPublicToken(token);
        return Map.of(
                "quotationNumber", q.getQuotationNumber(),
                "customerName", q.getCustomerName(),
                "companyName", q.getCompanyName() == null ? "" : q.getCompanyName(),
                "subtotal", q.getSubtotal(),
                "discount", q.getDiscount(),
                "tax", q.getTax(),
                "totalAmount", q.getTotalAmount(),
                "validUntil", q.getValidUntil(),
                "status", q.getStatus()
        );
    }

    @GetMapping("/quotations/{token}/pdf")
    public ResponseEntity<FileSystemResource> downloadPdf(@PathVariable String token) {
        Quotation q = quotationService.getByPublicToken(token);
        FileSystemResource resource = new FileSystemResource(q.getPdfPath());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + q.getQuotationNumber() + ".pdf\"")
                .body(resource);
    }

    @PostMapping("/quotations/{token}/accept")
    public Quotation accept(@PathVariable String token) {
        return quotationService.acceptQuotation(token);
    }

    @PostMapping("/quotations/{token}/request-changes")
    public Quotation requestChanges(@PathVariable String token, @Valid @RequestBody RequestChangesRequest request) {
        return quotationService.requestChanges(token, request);
    }
}
