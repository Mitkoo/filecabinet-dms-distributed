package com.filecabinet.integration.dto;

public record UpdateLineItemRequest(
        Integer lineNumber,
        String description,
        Double quantity,
        Double unitPrice,
        Double vatRatePercent,
        Double totalAmount,
        String category) {
}
