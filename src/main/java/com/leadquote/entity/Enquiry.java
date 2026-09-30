package com.leadquote.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "enquiries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Enquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id", nullable = false, unique = true)
    private Lead lead;

    @Column(name = "requirement", length = 2000)
    private String requirement;

    @Column(name = "product_or_service")
    private String productOrService;

    @Column(name = "quantity")
    private String quantity;

    @Column(name = "location")
    private String location;

    @Column(name = "required_date")
    private LocalDateTime requiredDate;

    @Column(name = "specifications", length = 2000)
    private String specifications;

    @Column(name = "material_preference")
    private String materialPreference;

    @Column(name = "additional_requirements", length = 2000)
    private String additionalRequirements;

    @Column(name = "remarks", length = 2000)
    private String remarks;

    // ---- Pre-Engineered Building (PEB) spec, matching the company's PEB request form ----

    /** CLEAR_SPAN, MULTI_SPAN_1, MULTI_SPAN_2, MULTI_SPAN_3, MULTI_GABLE, LEAN_TO, OTHER */
    @Column(name = "building_type")
    private String buildingType;

    @Column(name = "building_type_other")
    private String buildingTypeOther;

    @Column(name = "no_of_sheds")
    private String noOfSheds;

    /** LIGHT_FRAME, RIGID_FRAME */
    @Column(name = "end_frame_type")
    private String endFrameType;

    /** STEEL, RCC */
    @Column(name = "column_type")
    private String columnType;

    @Column(name = "span_width_m")
    private Double spanWidthM;

    @Column(name = "length_m")
    private Double lengthM;

    @Column(name = "clear_eave_height_m")
    private Double clearEaveHeightM;

    @Column(name = "eave_height_m")
    private Double eaveHeightM;

    /** CCGI, CCGL, BAREGALVALUME */
    @Column(name = "roof_cladding_material")
    private String roofCladdingMaterial;

    @Column(name = "roof_cladding_thickness")
    private String roofCladdingThickness;

    @Column(name = "roof_cladding_colour")
    private String roofCladdingColour;

    /** CCGI, CCGL, BAREGALVALUME */
    @Column(name = "side_cladding_material")
    private String sideCladdingMaterial;

    @Column(name = "side_cladding_thickness")
    private String sideCladdingThickness;

    @Column(name = "side_cladding_colour")
    private String sideCladdingColour;

    @Column(name = "crane_required")
    private Boolean craneRequired;

    @Column(name = "crane_type")
    private String craneType;

    @Column(name = "crane_capacity_tonnes")
    private String craneCapacityTonnes;

    @Column(name = "crane_height_m")
    private String craneHeightM;

    /** SUPPLY_EX_WORKS, SUPPLY_AT_SITE, OTHER */
    @Column(name = "scope_of_work")
    private String scopeOfWork;

    @Column(name = "scope_of_work_location")
    private String scopeOfWorkLocation;

    // ---- Extended building dimensions & finishing requirements ----

    @Column(name = "width_measurement_basis")
    private String widthMeasurementBasis;

    @Column(name = "length_measurement_basis")
    private String lengthMeasurementBasis;

    @Column(name = "intermediate_bay_spacing_m")
    private Double intermediateBaySpacingM;

    @Column(name = "end_bay_spacing_m")
    private Double endBaySpacingM;

    @Column(name = "brick_wall_height_m")
    private Double brickWallHeightM;

    @Column(name = "roof_slope")
    private String roofSlope;

    /** ROOF_ONLY, ROOF_AND_GABLE_END, ROOF_GABLE_END_AND_WALLS, FULL_ROOF_GABLE_END_AND_CLADDING */
    @Column(name = "roofing_cladding_requirement")
    private String roofingCladdingRequirement;

    /** CCGI, CCGL, BAREGALVALUME, OTHER */
    @Column(name = "main_shed_roof_material")
    private String mainShedRoofMaterial;

    @Column(name = "main_shed_roof_material_other")
    private String mainShedRoofMaterialOther;

    /** Comma-separated: MECHANICAL_CLEANING, SAND_BLASTING_ON_STEEL */
    @Column(name = "surface_preparation", length = 500)
    private String surfacePreparation;

    /** RED_OXIDE_PRIMER, ZINC_CHROMATE_RED_OXIDE_PRIMER, OTHER */
    @Column(name = "protective_coating")
    private String protectiveCoating;

    @Column(name = "protective_coating_other")
    private String protectiveCoatingOther;

    @Column(name = "skylight_required")
    private Boolean skylightRequired;

    @Column(name = "roof_ventilator_required")
    private Boolean roofVentilatorRequired;

    // ---- Ventilator system detail, gutters/downtake, canopy, louver, road facilities, declaration ----

    @Column(name = "roof_ventilator_diameter_throat")
    private String roofVentilatorDiameterThroat;

    @Column(name = "roof_ventilator_air_change_per_hr")
    private String roofVentilatorAirChangePerHr;

    @Column(name = "roof_ventilator_quantity")
    private String roofVentilatorQuantity;

    @Column(name = "ridge_vent_required")
    private Boolean ridgeVentRequired;

    @Column(name = "ridge_vent_size")
    private String ridgeVentSize;

    @Column(name = "ridge_vent_quantity")
    private String ridgeVentQuantity;

    @Column(name = "crane_size")
    private String craneSize;

    /** ROOF_STYLE_1..4 (see ROOF_STYLE_OPTIONS on the frontend) */
    @Column(name = "roof_style")
    private String roofStyle;

    /** Comma-separated: EAVE_GUTTER, VALLEY_GUTTER */
    @Column(name = "gutter_types")
    private String gutterTypes;

    /** PPGI, CCGL, BAREGALVALUME, OTHER */
    @Column(name = "gutter_material")
    private String gutterMaterial;

    @Column(name = "gutter_material_other")
    private String gutterMaterialOther;

    @Column(name = "downtake_pipes_required")
    private Boolean downtakePipesRequired;

    /** PVC, OTHER */
    @Column(name = "downtake_pipe_material")
    private String downtakePipeMaterial;

    @Column(name = "downtake_pipe_material_other")
    private String downtakePipeMaterialOther;

    @Column(name = "canopy_required")
    private Boolean canopyRequired;

    @Column(name = "canopy_size_oo")
    private String canopySizeOo;

    @Column(name = "canopy_height_ffl")
    private String canopyHeightFfl;

    @Column(name = "sheet_metal_louver_required")
    private Boolean sheetMetalLouverRequired;

    @Column(name = "sheet_metal_louver_size_oo")
    private String sheetMetalLouverSizeOo;

    @Column(name = "road_trailer_access")
    private Boolean roadTrailerAccess;

    @Column(name = "road_goods_stocking_detail", length = 1000)
    private String roadGoodsStockingDetail;

    @Column(name = "road_shutter_provision_detail", length = 1000)
    private String roadShutterProvisionDetail;

    @Column(name = "project_period")
    private String projectPeriod;

    @Column(name = "declarant_name")
    private String declarantName;

    @Column(name = "declarant_designation")
    private String declarantDesignation;

    @Column(name = "declarant_address", length = 500)
    private String declarantAddress;

    @Column(name = "declarant_sign_seal")
    private String declarantSignSeal;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}