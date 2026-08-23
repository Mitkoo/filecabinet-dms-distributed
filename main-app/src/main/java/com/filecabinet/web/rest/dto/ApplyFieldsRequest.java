package com.filecabinet.web.rest.dto;

import java.util.Map;

public record ApplyFieldsRequest(Map<String, String> fields) {
}
