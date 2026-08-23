package com.filecabinet.web.rest.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record WorkflowSummaryResponse(
        UUID id,
        UUID documentId,
        String documentTitle,
        String initiatorUsername,
        String status,
        LocalDateTime createdOn) {
}
