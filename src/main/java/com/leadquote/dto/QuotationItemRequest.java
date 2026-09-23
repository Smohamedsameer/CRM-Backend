package com.leadquote.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class QuotationItemRequest {
    private String description;
    private Double quantity;
    private BigDecimal unitPrice;
}
