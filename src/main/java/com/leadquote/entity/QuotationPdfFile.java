package com.leadquote.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Database copy of a quotation's PDF (the PEB PDF built in the browser, or the PDF the employee edited in Word
 * and uploaded). Hosts like Railway wipe the local disk on every redeploy; this copy lets the app restore the
 * exact same file instead of falling back to the basic auto-generated PDF.
 *
 * Kept in its own table (not a column on Quotation) so the PDF bytes are never loaded with the lead / quotation.
 */
@Entity
@Table(name = "quotation_pdf_files", indexes = {
        @Index(name = "idx_qpdf_quotation", columnList = "quotationId", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuotationPdfFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long quotationId;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGBLOB")
    private byte[] data;

    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
