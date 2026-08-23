package com.filecabinet.web.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 40) String name,
        @Size(max = 250) String description) {
}
