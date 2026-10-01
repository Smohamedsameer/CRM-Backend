package com.leadquote.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class QuotationGenerateRequest {
    /** Optional manual line items; if omitted, the engine derives a default line item from the lead's square footage. */
    private List<QuotationItemRequest> items;

    /** Optional overrides; otherwise pulled from configurable pricing rules. */
    private BigDecimal discount;
    private BigDecimal taxPercentage;

    /** PEB pricing: rate per sq ft typed by the admin. When set, the quotation is priced as area x rate. */
    private BigDecimal ratePerSqft;

    /** Optional override of the auto-calculated area (length x width, in sq ft). */
    private Double areaSqft;
}
