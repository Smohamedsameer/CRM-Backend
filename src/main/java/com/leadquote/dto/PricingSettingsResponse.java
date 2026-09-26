package com.leadquote.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingSettingsResponse {
    private BigDecimal ratePerSquareFoot;
    private BigDecimal defaultAdditionalCharges;
    private BigDecimal defaultDiscountPercentage;
    private BigDecimal defaultTaxPercentage;
}
