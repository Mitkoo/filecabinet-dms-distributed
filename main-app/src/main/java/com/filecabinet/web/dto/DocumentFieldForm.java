package com.filecabinet.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DocumentFieldForm {

    @NotBlank(message = "Field name is required.")
    @Size(max = 100, message = "Field name must be at most 100 characters.")
    private String fieldName;

    @NotBlank(message = "Field value is required.")
    @Size(max = 255, message = "Field value must be at most 255 characters.")
    private String fieldValue;
}
