package com.filecabinet.web.rest.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record DocumentDetailResponse(
        UUID id,
        String title,
        String documentType,
        String status,
        String filePath,
        LocalDateTime uploadedOn,
        UUID categoryId,
        String categoryName,
        String ownerUsername,
        List<FieldResponse> fields) {
}
