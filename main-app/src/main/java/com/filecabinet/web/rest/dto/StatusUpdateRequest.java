package com.filecabinet.web.rest.dto;

import com.filecabinet.document.model.DocumentStatus;
import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(@NotNull DocumentStatus status) {
}
