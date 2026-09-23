package com.leadquote.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "leads", indexes = {
        @Index(name = "idx_lead_code", columnList = "leadCode", unique = true),
        @Index(name = "idx_lead_phone", columnList = "phone"),
        @Index(name = "idx_lead_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-friendly unique ID, e.g. LEAD-1025 */
    @Column(nullable = false, unique = true, length = 40)
    private String leadCode;

    @Column(nullable = false)
    private String customerName;

    private String companyName;

    private Double squareFeet;

    private BigDecimal estimatedAmount;

    @Column(nullable = false)
    private String phone;

    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeadSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LeadStatus status = LeadStatus.NEW;

    /** Secure random token used to build the customer-facing enquiry form link. */
    @Column(unique = true, length = 100)
    private String enquiryToken;

    private LocalDateTime enquiryTokenExpiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Employee createdBy;

    @OneToOne(mappedBy = "lead", cascade = CascadeType.ALL, orphanRemoval = true)
    private Enquiry enquiry;

    @OneToMany(mappedBy = "lead", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Quotation> quotations = new ArrayList<>();

    @OneToMany(mappedBy = "lead", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WhatsAppMessage> whatsAppMessages = new ArrayList<>();

    @OneToOne(mappedBy = "lead", cascade = CascadeType.ALL, orphanRemoval = true)
    private Order order;

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
