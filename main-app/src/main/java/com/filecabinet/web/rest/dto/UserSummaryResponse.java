package com.filecabinet.web.rest.dto;

import java.util.UUID;

public record UserSummaryResponse(
        UUID id,
        String username,
        String email,
        String role,
        String fullName) {
}
