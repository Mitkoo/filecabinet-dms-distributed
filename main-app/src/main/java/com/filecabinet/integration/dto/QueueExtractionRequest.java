package com.filecabinet.integration.dto;

import java.util.UUID;

public record QueueExtractionRequest(UUID documentId, String sourcePath) {
}
