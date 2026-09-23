package com.leadquote.controller;

import com.leadquote.dto.LeadCreateRequest;
import com.leadquote.dto.LeadResponse;
import com.leadquote.entity.Lead;
import com.leadquote.entity.LeadStatus;
import com.leadquote.service.LeadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leads")
@RequiredArgsConstructor
public class LeadController {

    private final LeadService leadService;

    @PostMapping
    public LeadResponse create(@Valid @RequestBody LeadCreateRequest request, Authentication auth) {
        Lead lead = leadService.createLead(request, auth.getName());
        return LeadResponse.from(lead);
    }

    @GetMapping
    public Page<LeadResponse> list(@RequestParam(required = false) LeadStatus status,
                                    @RequestParam(required = false) String search,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        Page<Lead> leads = leadService.search(status, search,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return leads.map(LeadResponse::from);
    }

    @GetMapping("/{id}")
    public LeadResponse get(@PathVariable Long id) {
        return LeadResponse.from(leadService.getById(id));
    }

    @PutMapping("/{id}/status")
    public LeadResponse updateStatus(@PathVariable Long id, @RequestParam LeadStatus status) {
        return LeadResponse.from(leadService.updateStatus(id, status));
    }

    /** Employee-triggered retry if the automatic WhatsApp welcome message failed. */
    @PostMapping("/{id}/resend-welcome")
    public LeadResponse resendWelcome(@PathVariable Long id) {
        Lead lead = leadService.getById(id);
        return LeadResponse.from(leadService.triggerWelcomeWorkflow(lead));
    }

    /** Full lead detail incl. enquiry, quotations and WhatsApp message history, for the lead detail/timeline page. */
    @GetMapping("/{id}/detail")
    public Lead detail(@PathVariable Long id) {
        return leadService.getById(id);
    }
}
