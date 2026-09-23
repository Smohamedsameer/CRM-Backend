package com.leadquote.controller;

import com.leadquote.entity.Lead;
import com.leadquote.service.LeadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Employee-facing read access to a lead's enquiry (requires JWT auth, secured globally).
 * Customer-facing enquiry submission/lookup lives in PublicController under /api/public/**.
 */
@RestController
@RequestMapping("/api/enquiries")
@RequiredArgsConstructor
public class EnquiryController {

    private final LeadService leadService;

    @GetMapping("/lead/{leadId}")
    public Object getForLead(@PathVariable Long leadId) {
        Lead lead = leadService.getById(leadId);
        return lead.getEnquiry();
    }
}
