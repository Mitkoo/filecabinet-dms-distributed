package com.filecabinet.web.rest.dto;

import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
        @Size(max = 120) String fullName,
        @Size(max = 40) String phone,
        @Size(max = 80) String jobTitle,
        @Size(max = 120) String companyName,
        @Size(max = 200) String companyAddress) {
}
