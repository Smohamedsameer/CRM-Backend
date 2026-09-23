package com.leadquote.controller;

import com.leadquote.entity.Lead;
import com.leadquote.entity.Order;
import com.leadquote.entity.Quotation;
import com.leadquote.service.LeadService;
import com.leadquote.service.OrderService;
import com.leadquote.service.QuotationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final LeadService leadService;
    private final QuotationService quotationService;

    @PostMapping("/confirm/{leadId}")
    public Order confirm(@PathVariable Long leadId,
                          @RequestParam(required = false) Long quotationId,
                          @RequestParam(required = false) String notes) {
        Lead lead = leadService.getById(leadId);
        Quotation quotation = quotationId != null ? quotationService.getById(quotationId) : null;
        return orderService.confirmOrder(lead, quotation, notes);
    }
}
