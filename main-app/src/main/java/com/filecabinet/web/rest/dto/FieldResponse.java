package com.filecabinet.web.rest.dto;

import java.util.UUID;

public record FieldResponse(
        UUID id,
        String fieldName,
        String fieldValue,
        Double confidence) {
}
