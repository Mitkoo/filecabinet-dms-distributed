package com.filecabinet.extraction.ai;

public record FieldBox(
        int page,
        double x,
        double y,
        double width,
        double height,
        double pageWidth,
        double pageHeight) {
}
