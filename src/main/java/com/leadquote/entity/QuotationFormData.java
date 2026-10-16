package com.leadquote.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * The details the employee entered in the quotation builder (ref no, prices, cover, notes ...) as JSON.
 * It lets the app build the Word version of a quotation later, from the Quotations list, not only
 * right after it was generated. Kept in its own table so it is never loaded with the lead / quotation.
 */
@Entity
@Table(name = "quotation_form_data", indexes = {
        @Index(name = "idx_qform_quotation", columnList = "quotationId", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuotationFormData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long quotationId;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String json;

    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
