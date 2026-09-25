package com.leadquote.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** One row on the employee "Client Request" page: a customer's "request changes" note, and any admin reply. */
@Data
@Builder
public class ClientRequestResponse {
    private Long id;
    private Long leadId;
    private String leadCode;
    private String customerName;
    private String companyName;
    private String phone;
    private Long quotationId;
    private String quotationNumber;
    private String changeRequestNotes;
    private LocalDateTime createdAt;
    private String adminReply;
    private LocalDateTime repliedAt;
}
