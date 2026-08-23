package com.filecabinet.extraction.ai;

public record ExtractedFieldData(String name, String value, double confidence, FieldBox box) {

    public ExtractedFieldData(String name, String value, double confidence) {
        this(name, value, confidence, null);
    }
}
