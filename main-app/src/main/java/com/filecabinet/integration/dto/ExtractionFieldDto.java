package com.filecabinet.integration.dto;

import java.util.UUID;

public record ExtractionFieldDto(UUID id, String fieldName, String fieldValue, double confidence, FieldBoxDto box) {
}
