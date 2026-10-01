package com.leadquote.dto;

import com.leadquote.entity.Lead;
import com.leadquote.entity.LeadSource;
import com.leadquote.entity.LeadStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class LeadResponse {
    private Long id;
    private String leadCode;
    private String customerName;
    private String companyName;
    private Double squareFeet;
    private BigDecimal estimatedAmount;
    private String phone;
    private String email;
    private LeadSource source;
    private LeadStatus status;
    private LocalDateTime createdAt;

    public static LeadResponse from(Lead lead) {
        return LeadResponse.builder()
                .id(lead.getId())
                .leadCode(lead.getLeadCode())
                .customerName(lead.getCustomerName())
                .companyName(lead.getCompanyName())
                .squareFeet(lead.getSquareFeet())
                .estimatedAmount(lead.getEstimatedAmount())
                .phone(lead.getPhone())
                .email(lead.getEmail())
                .source(lead.getSource())
                .status(lead.getStatus())
                .createdAt(lead.getCreatedAt())
                .build();
    }
}
