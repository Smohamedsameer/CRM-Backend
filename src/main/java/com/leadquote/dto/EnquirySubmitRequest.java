package com.leadquote.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class EnquirySubmitRequest {

    @NotBlank
    private String token;

    @NotBlank
    private String requirement;

    private String productOrService;
    private String quantity;
    private String location;
    private LocalDateTime requiredDate;
    private String specifications;
    private String materialPreference;
    private String additionalRequirements;
    private String remarks;

    // ---- PEB spec ----
    private String buildingType;
    private String buildingTypeOther;
    private String noOfSheds;
    private String endFrameType;
    private String columnType;
    private Double spanWidthM;
    private Double lengthM;
    private Double clearEaveHeightM;
    private Double eaveHeightM;
    private String roofCladdingMaterial;
    private String roofCladdingThickness;
    private String roofCladdingColour;
    private String sideCladdingMaterial;
    private String sideCladdingThickness;
    private String sideCladdingColour;
    private Boolean craneRequired;
    private String craneType;
    private String craneCapacityTonnes;
    private String craneHeightM;
    private String scopeOfWork;
    private String scopeOfWorkLocation;
}
