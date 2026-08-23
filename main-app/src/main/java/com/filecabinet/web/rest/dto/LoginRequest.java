package com.filecabinet.web.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password) {
}
