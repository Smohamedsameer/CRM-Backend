package com.leadquote.service;

import com.leadquote.config.CompanyProperties;
import com.leadquote.dto.*;
import com.leadquote.entity.AppSetting;
import com.leadquote.entity.Employee;
import com.leadquote.repository.AppSettingRepository;
import com.leadquote.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Backs the Settings page, and is also the read path other services should use for company/pricing
 * values that are meant to be editable at runtime (see getCompanyName() etc. below) - a DB row in
 * app_settings overrides the application.yml / .env default from CompanyProperties or PricingRules,
 * so nothing breaks for an install that has never opened the Settings page.
 */
@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final String COMPANY_NAME = "company.name";
    private static final String COMPANY_PHONE = "company.phone";
    private static final String COMPANY_EMAIL = "company.email";
    private static final String COMPANY_ADDRESS = "company.address";
    private static final String COMPANY_GST = "company.gstNumber";

    private static final String PRICE_RATE_PER_SQFT = "pricing.ratePerSquareFoot";
    private static final String PRICE_ADDITIONAL_CHARGES = "pricing.defaultAdditionalCharges";
    private static final String PRICE_DISCOUNT_PCT = "pricing.defaultDiscountPercentage";
    private static final String PRICE_TAX_PCT = "pricing.defaultTaxPercentage";

    private final AppSettingRepository appSettingRepository;
    private final CompanyProperties companyProperties;
    private final PricingRules pricingRules;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;

    // ---- Live values for other services to read (see class comment) ----

    public String getCompanyName() { return get(COMPANY_NAME, companyProperties.getName()); }
    public String getCompanyPhone() { return get(COMPANY_PHONE, companyProperties.getPhone()); }
    public String getCompanyEmail() { return get(COMPANY_EMAIL, companyProperties.getEmail()); }
    public String getCompanyAddress() { return get(COMPANY_ADDRESS, companyProperties.getAddress()); }
    public String getCompanyGstNumber() { return get(COMPANY_GST, companyProperties.getGstNumber()); }

    public BigDecimal getRatePerSquareFoot() { return getDecimal(PRICE_RATE_PER_SQFT, pricingRules.getRatePerSquareFoot()); }
    public BigDecimal getDefaultAdditionalCharges() { return getDecimal(PRICE_ADDITIONAL_CHARGES, pricingRules.getDefaultAdditionalCharges()); }
    public BigDecimal getDefaultDiscountPercentage() { return getDecimal(PRICE_DISCOUNT_PCT, pricingRules.getDefaultDiscountPercentage()); }
    public BigDecimal getDefaultTaxPercentage() { return getDecimal(PRICE_TAX_PCT, pricingRules.getDefaultTaxPercentage()); }

    // ---- Settings page ----

    public CompanySettingsResponse getCompanySettings() {
        return CompanySettingsResponse.builder()
                .name(getCompanyName())
                .phone(getCompanyPhone())
                .email(getCompanyEmail())
                .address(getCompanyAddress())
                .gstNumber(getCompanyGstNumber())
                .build();
    }

    @Transactional
    public CompanySettingsResponse updateCompanySettings(CompanySettingsRequest request) {
        set(COMPANY_NAME, request.getName());
        set(COMPANY_PHONE, request.getPhone());
        set(COMPANY_EMAIL, request.getEmail());
        set(COMPANY_ADDRESS, request.getAddress());
        set(COMPANY_GST, request.getGstNumber());
        return getCompanySettings();
    }

    public PricingSettingsResponse getPricingSettings() {
        return PricingSettingsResponse.builder()
                .ratePerSquareFoot(getRatePerSquareFoot())
                .defaultAdditionalCharges(getDefaultAdditionalCharges())
                .defaultDiscountPercentage(getDefaultDiscountPercentage())
                .defaultTaxPercentage(getDefaultTaxPercentage())
                .build();
    }

    @Transactional
    public PricingSettingsResponse updatePricingSettings(PricingSettingsRequest request) {
        set(PRICE_RATE_PER_SQFT, request.getRatePerSquareFoot().toString());
        set(PRICE_ADDITIONAL_CHARGES, request.getDefaultAdditionalCharges().toString());
        set(PRICE_DISCOUNT_PCT, request.getDefaultDiscountPercentage().toString());
        set(PRICE_TAX_PCT, request.getDefaultTaxPercentage().toString());
        return getPricingSettings();
    }

    @Transactional
    public void changePassword(String employeeEmail, String currentPassword, String newPassword) {
        Employee employee = employeeRepository.findByEmailIgnoreCase(employeeEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(currentPassword, employee.getPasswordHash())) {
            throw new BadCredentialsException("Current password is incorrect");
        }
        employee.setPasswordHash(passwordEncoder.encode(newPassword));
        employeeRepository.save(employee);
    }

    // ---- helpers ----

    private String get(String key, String fallback) {
        return appSettingRepository.findById(key)
                .map(AppSetting::getSettingValue)
                .filter(v -> v != null && !v.isBlank())
                .orElse(fallback);
    }

    private BigDecimal getDecimal(String key, BigDecimal fallback) {
        String raw = get(key, null);
        if (raw == null) return fallback;
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void set(String key, String value) {
        AppSetting setting = appSettingRepository.findById(key).orElseGet(() -> AppSetting.builder().settingKey(key).build());
        setting.setSettingValue(value);
        appSettingRepository.save(setting);
    }
}
