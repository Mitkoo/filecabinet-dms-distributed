package com.filecabinet.integration.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ExtractionJobDto(
        UUID id,
        UUID documentId,
        String provider,
        String status,
        int attempts,
        LocalDateTime requestedOn,
        LocalDateTime completedOn,
        boolean needsReview,
        List<String> reviewNotes,
        List<ExtractionFieldDto> fields,
        List<LineItemDto> lineItems) {
}
