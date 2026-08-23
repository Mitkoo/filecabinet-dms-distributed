package com.filecabinet.extraction.ai;

import java.util.List;

public record ExtractionResult(List<ExtractedFieldData> fields, List<LineItemData> lineItems) {
}
