package com.filecabinet.web.rest.dto;

import java.util.UUID;

public record CategoryResponse(UUID id, String name, String description) {
}
