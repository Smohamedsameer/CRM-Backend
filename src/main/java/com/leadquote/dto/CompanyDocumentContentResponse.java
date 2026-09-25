package com.leadquote.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** One file's bytes (base64) for the in-app viewer. */
@Data
@AllArgsConstructor
public class CompanyDocumentContentResponse {
    private Long id;
    private String fileName;
    private String contentType;
    private String dataBase64;
}
