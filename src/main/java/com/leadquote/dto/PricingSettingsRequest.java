package com.leadquote.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PricingSettingsRequest {
    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal ratePerSquareFoot;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal defaultAdditionalCharges;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal defaultDiscountPercentage;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal defaultTaxPercentage;
}
