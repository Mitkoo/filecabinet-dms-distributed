package com.filecabinet.extraction.web.dto;

import java.util.UUID;

public record ExtractedFieldResponse(
        UUID id,
        String fieldName,
        String fieldValue,
        double confidence,
        FieldBoxResponse box) {
}
