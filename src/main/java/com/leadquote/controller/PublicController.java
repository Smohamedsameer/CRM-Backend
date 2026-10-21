package com.leadquote.controller;

import com.leadquote.dto.EnquirySubmitRequest;
import com.leadquote.dto.RequestChangesRequest;
import com.leadquote.entity.Enquiry;
import com.leadquote.config.CompanyProperties;
import com.leadquote.entity.Lead;
import com.leadquote.entity.LeadStatus;
import com.leadquote.entity.Quotation;
import com.leadquote.service.EnquiryService;
import com.leadquote.service.LeadService;
import com.leadquote.service.QuotationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
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
    private final CompanyProperties companyProperties;

    // ---- Enquiry form --------------------------------------------------

    @GetMapping("/enquiry/{token}")
    public Map<String, Object> getEnquiryFormPrefill(@PathVariable String token) {
        Lead lead = leadService.getByEnquiryToken(token);
        return Map.of(
                "customerName", lead.getCustomerName(),
                "companyName", lead.getCompanyName() == null ? "" : lead.getCompanyName(),
                "address", lead.getAddress() == null ? "" : lead.getAddress(),
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
        Lead lead = q.getLead();
        // The page shows one status: "Called to order" once the customer has tapped the call button.
        String status = lead != null && lead.getStatus() == LeadStatus.CALLED_TO_ORDER
                ? LeadStatus.CALLED_TO_ORDER.name() : q.getStatus().name();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("quotationNumber", q.getQuotationNumber());
        body.put("customerName", q.getCustomerName());
        body.put("companyName", q.getCompanyName() == null ? "" : q.getCompanyName());
        body.put("phone", lead == null || lead.getPhone() == null ? "" : lead.getPhone());
        body.put("address", lead == null || lead.getAddress() == null ? "" : lead.getAddress());
        body.put("totalAmount", q.getTotalAmount());
        body.put("validUntil", q.getValidUntil());
        body.put("status", status);
        body.put("callPhone", companyProperties.getMdPhone());
        return body;
    }

    /** "Call to confirm order" button: records "Called to order" on the lead (the phone dialer itself opens on the customer's device). */
    @PostMapping("/quotations/{token}/called-to-order")
    public Map<String, Object> calledToOrder(@PathVariable String token) {
        quotationService.markCalledToOrder(token);
        return Map.of("status", LeadStatus.CALLED_TO_ORDER.name());
    }

    @GetMapping("/quotations/{token}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String token) throws java.io.IOException {
        Quotation q = quotationService.getByPublicTokenForPdf(token);
        byte[] bytes = java.nio.file.Files.readAllBytes(quotationService.ensurePdfFile(q).toPath());
        String fileName = q.getQuotationNumber().replace("/", "-") + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(bytes.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(bytes);
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
