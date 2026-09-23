package com.leadquote.controller;

import com.leadquote.entity.LeadStatus;
import com.leadquote.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final LeadRepository leadRepository;

    @GetMapping("/summary")
    public Map<String, Long> summary() {
        return Map.of(
                "totalLeads", leadRepository.count(),
                "newLeads", leadRepository.countByStatus(LeadStatus.NEW),
                "formsPending", leadRepository.countByStatus(LeadStatus.FORM_SENT)
                        + leadRepository.countByStatus(LeadStatus.WELCOME_SENT),
                "formsSubmitted", leadRepository.countByStatus(LeadStatus.FORM_SUBMITTED),
                "quotationsSent", leadRepository.countByStatus(LeadStatus.QUOTATION_SENT),
                "quotationsAccepted", leadRepository.countByStatus(LeadStatus.ACCEPTED),
                "changesRequested", leadRepository.countByStatus(LeadStatus.CHANGES_REQUESTED),
                "ordersConfirmed", leadRepository.countByStatus(LeadStatus.ORDER_CONFIRMED)
        );
    }
}
