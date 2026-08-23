package com.filecabinet.web.rest.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record WorkflowResponse(
        UUID id,
        UUID documentId,
        String documentTitle,
        String status,
        String initiatorUsername,
        String message,
        LocalDateTime createdOn,
        LocalDateTime completedOn,
        List<StepResponse> steps,
        List<EventResponse> events) {
}
