package com.filecabinet.web.rest.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record StepResponse(
        UUID id,
        int stepOrder,
        String reviewerUsername,
        String status,
        String comment,
        LocalDateTime decidedOn) {
}
