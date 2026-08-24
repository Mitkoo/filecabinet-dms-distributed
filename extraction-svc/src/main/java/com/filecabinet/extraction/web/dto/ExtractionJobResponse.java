package com.filecabinet.extraction.web.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ExtractionJobResponse(
        UUID id,
        UUID documentId,
        String provider,
        String status,
        int attempts,
        LocalDateTime requestedOn,
        LocalDateTime completedOn,
        boolean needsReview,
        List<String> reviewNotes,
        List<ExtractedFieldResponse> fields,
        List<LineItemResponse> lineItems) {
}
