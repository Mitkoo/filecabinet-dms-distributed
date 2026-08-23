package com.filecabinet.extraction.ai;

public interface ExtractorPort {

    ExtractionResult extract(byte[] fileBytes, String filename);

    String providerName();
}
