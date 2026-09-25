package com.leadquote.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ClientRequestReplyRequest {
    @NotBlank
    private String message;
}
