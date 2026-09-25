package com.leadquote.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** A file on the "Certificates & Documents" page (GST certificate, work order, MTC, insurance, ...). */
@Entity
@Table(name = "company_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Section heading, e.g. "Company Documents". */
    @Column(nullable = false, length = 100)
    private String category;

    /** Document name within the section, e.g. "GST Certificate". */
    @Column(name = "doc_type", nullable = false, length = 150)
    private String docType;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false, length = 150)
    private String contentType;

    @Column(nullable = false)
    private Long sizeBytes;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGBLOB")
    private byte[] data;

    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    @PrePersist
    void onCreate() {
        uploadedAt = LocalDateTime.now();
    }
}
