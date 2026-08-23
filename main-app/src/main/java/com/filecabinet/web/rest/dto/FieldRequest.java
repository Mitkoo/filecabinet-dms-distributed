package com.filecabinet.web.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FieldRequest(
        @NotBlank @Size(max = 100) String fieldName,
        @Size(max = 255) String fieldValue) {
}
