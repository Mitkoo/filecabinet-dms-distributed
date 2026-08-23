package com.filecabinet.web.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DocumentUpdateRequest(
        @NotBlank String title,
        @NotBlank String documentType,
        @NotNull UUID categoryId) {
}
