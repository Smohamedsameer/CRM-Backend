package com.leadquote.service;

import com.leadquote.dto.ClientRequestResponse;
import com.leadquote.entity.CustomerResponse;
import com.leadquote.entity.Lead;
import com.leadquote.entity.QuotationStatus;
import com.leadquote.exception.NotFoundException;
import com.leadquote.repository.CustomerResponseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Backs the employee "Client Request" page: every customer "request changes" note, with the
 * ability for an admin to answer it - the reply is sent straight to the customer's WhatsApp.
 */
@Service
@RequiredArgsConstructor
public class ClientRequestService {

    private final CustomerResponseRepository customerResponseRepository;
    private final WhatsAppService whatsAppService;

    public List<ClientRequestResponse> list() {
        return customerResponseRepository.findByResponseTypeOrderByCreatedAtDesc(QuotationStatus.CHANGES_REQUESTED)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ClientRequestResponse reply(Long id, String message) {
        CustomerResponse response = customerResponseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Client request not found: " + id));
        Lead lead = response.getQuotation().getLead();

        whatsAppService.sendFollowUpMessage(lead, message);

        response.setAdminReply(message);
        response.setRepliedAt(LocalDateTime.now());
        return toResponse(customerResponseRepository.save(response));
    }

    private ClientRequestResponse toResponse(CustomerResponse response) {
        Lead lead = response.getQuotation().getLead();
        return ClientRequestResponse.builder()
                .id(response.getId())
                .leadId(lead.getId())
                .leadCode(lead.getLeadCode())
                .customerName(lead.getCustomerName())
                .companyName(lead.getCompanyName())
                .phone(lead.getPhone())
                .quotationId(response.getQuotation().getId())
                .quotationNumber(response.getQuotation().getQuotationNumber())
                .changeRequestNotes(response.getChangeRequestNotes())
                .createdAt(response.getCreatedAt())
                .adminReply(response.getAdminReply())
                .repliedAt(response.getRepliedAt())
                .build();
    }
}
