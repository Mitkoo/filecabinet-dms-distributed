package com.filecabinet.extraction.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateExtractionRequest(
        @NotNull UUID documentId,
        @NotBlank String sourcePath) {
}
