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

    // ---- Extended building dimensions & finishing requirements ----
    private String widthMeasurementBasis;
    private String lengthMeasurementBasis;
    private Double intermediateBaySpacingM;
    private Double endBaySpacingM;
    private Double brickWallHeightM;
    private String roofSlope;
    private String roofingCladdingRequirement;
    private String mainShedRoofMaterial;
    private String mainShedRoofMaterialOther;
    private String surfacePreparation;
    private String protectiveCoating;
    private String protectiveCoatingOther;
    private Boolean skylightRequired;
    private Boolean roofVentilatorRequired;

    // ---- Ventilator system detail, gutters/downtake, canopy, louver, road facilities, declaration ----
    private String roofVentilatorDiameterThroat;
    private String roofVentilatorAirChangePerHr;
    private String roofVentilatorQuantity;
    private Boolean ridgeVentRequired;
    private String ridgeVentSize;
    private String ridgeVentQuantity;
    private String craneSize;
    private String roofStyle;
    private String gutterTypes;
    private String gutterMaterial;
    private String gutterMaterialOther;
    private Boolean downtakePipesRequired;
    private String downtakePipeMaterial;
    private String downtakePipeMaterialOther;
    private Boolean canopyRequired;
    private String canopySizeOo;
    private String canopyHeightFfl;
    private Boolean sheetMetalLouverRequired;
    private String sheetMetalLouverSizeOo;
    private Boolean roadTrailerAccess;
    private String roadGoodsStockingDetail;
    private String roadShutterProvisionDetail;
    private String projectPeriod;
    private String declarantName;
    private String declarantDesignation;
    private String declarantAddress;
    private String declarantSignSeal;
}
