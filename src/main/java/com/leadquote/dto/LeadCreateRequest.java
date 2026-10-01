package com.leadquote.dto;

import com.leadquote.entity.LeadSource;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

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

    private String address;

    @Pattern(regexp = "^$|^[0-9]{6}$", message = "Pincode must be 6 digits")
    private String pincode;

    @Pattern(regexp = "^$|^[0-9A-Za-z]{15}$", message = "GST number must be 15 characters")
    private String gstNumber;

    private String projectName;

    /** Date to print on the quotation. */
    private LocalDate quotationDate;

    /** Cover photo as a data URL ("data:image/jpeg;base64,...") - optional. */
    private String coverImage;
}
