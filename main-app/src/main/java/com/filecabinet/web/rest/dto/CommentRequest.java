package com.filecabinet.web.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentRequest(@NotBlank @Size(max = 500) String message) {
}
