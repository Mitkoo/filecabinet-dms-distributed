package com.filecabinet.web.rest.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String title,
        String documentType,
        String status,
        LocalDateTime uploadedOn,
        String categoryName,
        String ownerUsername) {
}
