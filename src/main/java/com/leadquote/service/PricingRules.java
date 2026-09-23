package com.leadquote.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Configurable business pricing rules, intentionally kept OUT of controllers.
 * Values are sourced from application.yml / environment so they can be changed
 * without a code deployment. For more advanced needs, swap this component's
 * implementation to read rules from a database table instead.
 */
@Component
@ConfigurationProperties(prefix = "pricing")
@Getter
@Setter
public class PricingRules {

    /** Default rate per square foot, used when the enquiry doesn't specify explicit line items. */
    private BigDecimal ratePerSquareFoot = new BigDecimal("20.00");

    /** Flat additional charges applied to every quotation (installation, logistics, etc.) */
    private BigDecimal defaultAdditionalCharges = BigDecimal.ZERO;

    /** Default discount percentage (0-100) applied unless overridden per-quotation. */
    private BigDecimal defaultDiscountPercentage = BigDecimal.ZERO;

    /** Default tax percentage (e.g. GST 18). */
    private BigDecimal defaultTaxPercentage = new BigDecimal("18.00");
}
