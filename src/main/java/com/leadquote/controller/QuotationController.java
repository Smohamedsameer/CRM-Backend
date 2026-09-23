package com.leadquote.controller;

import com.leadquote.dto.QuotationGenerateRequest;
import com.leadquote.entity.Lead;
import com.leadquote.entity.Quotation;
import com.leadquote.service.LeadService;
import com.leadquote.service.QuotationService;
import lombok.RequiredArgsConstructor;
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
}
