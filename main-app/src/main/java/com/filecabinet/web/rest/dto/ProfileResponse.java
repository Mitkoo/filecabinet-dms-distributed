package com.filecabinet.web.rest.dto;

import java.util.UUID;

public record ProfileResponse(
        UUID id,
        String username,
        String email,
        String role,
        String fullName,
        String phone,
        String jobTitle,
        String companyName,
        String companyAddress) {
}
