package com.leadquote.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Upload payload: the file is sent base64-encoded inside JSON, like every other API call. */
@Data
public class CompanyDocumentUploadRequest {
    @NotBlank @Size(max = 100)
    private String category;
    @NotBlank @Size(max = 150)
    private String docType;
    @NotBlank @Size(max = 255)
    private String fileName;
    @NotBlank @Size(max = 150)
    private String contentType;
    @NotBlank
    private String dataBase64;
}
