package com.leadquote.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RequestChangesRequest {
    @NotBlank
    private String notes;
}
