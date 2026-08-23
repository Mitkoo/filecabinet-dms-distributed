package com.filecabinet.web.rest.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String eventType,
        String actorUsername,
        String message,
        LocalDateTime createdOn) {
}
