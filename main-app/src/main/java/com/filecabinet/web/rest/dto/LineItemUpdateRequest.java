package com.filecabinet.web.rest.dto;

public record LineItemUpdateRequest(
        Integer lineNumber,
        String description,
        Double quantity,
        Double unitPrice,
        Double vatRatePercent,
        Double totalAmount,
        String category) {
}
