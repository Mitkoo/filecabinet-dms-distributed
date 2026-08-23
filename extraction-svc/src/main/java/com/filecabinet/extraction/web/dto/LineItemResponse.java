package com.filecabinet.extraction.web.dto;

import java.util.UUID;

public record LineItemResponse(
        UUID id,
        Integer lineNumber,
        String description,
        Double quantity,
        Double unitPrice,
        Double vatRatePercent,
        Double totalAmount,
        String category,
        FieldBoxResponse box) {
}
