package com.leadquote.controller;

import com.leadquote.dto.QuotationGenerateRequest;
import com.leadquote.entity.Lead;
import com.leadquote.entity.Quotation;
import com.leadquote.service.LeadService;
import com.leadquote.service.QuotationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quotations")
@RequiredArgsConstructor
public class QuotationController {

    private final QuotationService quotationService;
    private final LeadService leadService;

    @PostMapping("/generate/{leadId}")
    public Quotation generate(@PathVariable Long leadId, @RequestBody(required = false) QuotationGenerateRequest request) {
        Lead lead = leadService.getById(leadId);
        return quotationService.generateQuotation(lead, request);
    }

    @GetMapping("/{id}")
    public Quotation get(@PathVariable Long id) {
        return quotationService.getById(id);
    }

    @PostMapping("/{id}/send")
    public Quotation send(@PathVariable Long id) {
        Quotation quotation = quotationService.getById(id);
        return quotationService.sendQuotation(quotation);
    }

    /** Lets a logged-in employee review any quotation's PDF, regardless of status - the public
     * PDF link (/api/public/quotations/{token}/pdf) only works for the customer's own token. */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) throws java.io.IOException {
        Quotation quotation = quotationService.getById(id);
        byte[] bytes = java.nio.file.Files.readAllBytes(quotationService.ensurePdfFile(quotation).toPath());
        String fileName = quotation.getQuotationNumber().replace("/", "-") + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(bytes.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(bytes);
    }
}
