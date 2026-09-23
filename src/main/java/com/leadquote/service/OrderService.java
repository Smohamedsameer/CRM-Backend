package com.leadquote.service;

import com.leadquote.entity.Lead;
import com.leadquote.entity.LeadStatus;
import com.leadquote.entity.Order;
import com.leadquote.entity.Quotation;
import com.leadquote.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final AuditService auditService;

    @Transactional
    public Order confirmOrder(Lead lead, Quotation quotation, String notes) {
        Order order = Order.builder()
                .lead(lead)
                .quotation(quotation)
                .orderNumber("ORD-" + lead.getLeadCode().replace("LEAD-", ""))
                .orderAmount(quotation != null ? quotation.getTotalAmount() : null)
                .status("CONFIRMED")
                .notes(notes)
                .build();

        Order saved = orderRepository.save(order);
        lead.setStatus(LeadStatus.ORDER_CONFIRMED);
        auditService.log("Order", saved.getId(), "ORDER_CONFIRMED", "Order confirmed for lead " + lead.getLeadCode());
        return saved;
    }
}
