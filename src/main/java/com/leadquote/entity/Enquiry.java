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

    @Column(length = 2000)
    private String requirement;

    private String productOrService;

    private String quantity;

    private String location;

    private LocalDateTime requiredDate;

    @Column(length = 2000)
    private String specifications;

    private String materialPreference;

    @Column(length = 2000)
    private String additionalRequirements;

    @Column(length = 2000)
    private String remarks;

    // ---- Pre-Engineered Building (PEB) spec, matching the company's PEB request form ----

    /** CLEAR_SPAN, MULTI_SPAN_1, MULTI_SPAN_2, MULTI_SPAN_3, MULTI_GABLE, LEAN_TO, OTHER */
    private String buildingType;
    private String buildingTypeOther;
    private String noOfSheds;

    /** LIGHT_FRAME, RIGID_FRAME */
    private String endFrameType;
    /** STEEL, RCC */
    private String columnType;

    private Double spanWidthM;
    private Double lengthM;
    private Double clearEaveHeightM;
    private Double eaveHeightM;

    /** CCGI, CCGL, BAREGALVALUME */
    private String roofCladdingMaterial;
    private String roofCladdingThickness;
    private String roofCladdingColour;

    /** CCGI, CCGL, BAREGALVALUME */
    private String sideCladdingMaterial;
    private String sideCladdingThickness;
    private String sideCladdingColour;

    private Boolean craneRequired;
    private String craneType;
    private String craneCapacityTonnes;
    private String craneHeightM;

    /** SUPPLY_EX_WORKS, SUPPLY_AT_SITE, OTHER */
    private String scopeOfWork;
    private String scopeOfWorkLocation;

    private LocalDateTime createdAt;
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