package com.filecabinet.web.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record RoleUpdateRequest(@NotBlank String role) {
}
