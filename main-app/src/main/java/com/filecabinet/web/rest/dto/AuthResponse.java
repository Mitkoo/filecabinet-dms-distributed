package com.filecabinet.web.rest.dto;

public record AuthResponse(
        String token,
        String username,
        String role) {
}
