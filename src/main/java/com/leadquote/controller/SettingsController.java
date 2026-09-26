package com.leadquote.controller;

import com.leadquote.dto.*;
import com.leadquote.service.SettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping("/company")
    public CompanySettingsResponse getCompany() {
        return settingsService.getCompanySettings();
    }

    @PutMapping("/company")
    public CompanySettingsResponse updateCompany(@Valid @RequestBody CompanySettingsRequest request) {
        return settingsService.updateCompanySettings(request);
    }

    @GetMapping("/pricing")
    public PricingSettingsResponse getPricing() {
        return settingsService.getPricingSettings();
    }

    @PutMapping("/pricing")
    public PricingSettingsResponse updatePricing(@Valid @RequestBody PricingSettingsRequest request) {
        return settingsService.updatePricingSettings(request);
    }

    @PostMapping("/change-password")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication authentication) {
        settingsService.changePassword(authentication.getName(), request.getCurrentPassword(), request.getNewPassword());
    }
}
