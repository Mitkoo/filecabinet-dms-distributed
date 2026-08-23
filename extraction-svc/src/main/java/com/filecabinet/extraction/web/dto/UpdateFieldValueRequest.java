package com.filecabinet.extraction.web.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateFieldValueRequest(@NotBlank String fieldValue) {
}
