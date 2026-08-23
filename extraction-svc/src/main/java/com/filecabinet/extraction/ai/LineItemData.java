package com.filecabinet.extraction.ai;

public record LineItemData(
        Integer lineNumber,
        String description,
        Double quantity,
        Double unitPrice,
        Double vatRatePercent,
        Double totalAmount,
        String category,
        FieldBox box) {

    public LineItemData(Integer lineNumber, String description, Double quantity, Double unitPrice,
                        Double vatRatePercent, Double totalAmount, String category) {
        this(lineNumber, description, quantity, unitPrice, vatRatePercent, totalAmount, category, null);
    }
}
