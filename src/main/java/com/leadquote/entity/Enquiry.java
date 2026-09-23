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