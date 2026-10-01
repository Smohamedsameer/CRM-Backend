package com.leadquote.dto;

import com.leadquote.entity.LeadSource;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LeadCreateRequest {

    @NotBlank
    private String customerName;

    private String companyName;

    private Double squareFeet;

    private BigDecimal estimatedAmount;

    @NotBlank
    @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Invalid phone number")
    private String phone;

    @Email
    private String email;

    @NotNull
    private LeadSource source;
}
