package com.filecabinet.web.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record FieldValueUpdateRequest(@NotBlank String fieldValue) {
}
