package com.filecabinet.extraction.web.dto;

public record FieldBoxResponse(
        int page,
        double x,
        double y,
        double width,
        double height,
        double pageWidth,
        double pageHeight) {
}
