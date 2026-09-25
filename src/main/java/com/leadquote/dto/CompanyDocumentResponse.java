package com.leadquote.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** File metadata for the documents list - no file bytes, so the list stays light. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDocumentResponse {
    private Long id;
    private String category;
    private String docType;
    private String fileName;
    private String contentType;
    private Long sizeBytes;
    private LocalDateTime uploadedAt;
}
