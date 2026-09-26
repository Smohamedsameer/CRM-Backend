package com.leadquote.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CompanySettingsRequest {
    @NotBlank
    private String name;
    private String phone;
    private String email;
    private String address;
    private String gstNumber;
}
