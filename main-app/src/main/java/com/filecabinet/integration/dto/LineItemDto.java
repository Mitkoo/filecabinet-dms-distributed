package com.filecabinet.integration.dto;

import java.util.UUID;

public record LineItemDto(
        UUID id,
        Integer lineNumber,
        String description,
        Double quantity,
        Double unitPrice,
        Double vatRatePercent,
        Double totalAmount,
        String category,
        FieldBoxDto box) {
}
