package com.filecabinet.web.dto;

import com.filecabinet.document.model.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class UploadForm {

    @NotBlank(message = "Title is required.")
    @Size(min = 5, max = 100, message = "Title must be 5-100 characters.")
    private String title;

    @NotNull(message = "Document type is required.")
    private DocumentType documentType;

    @NotNull(message = "Category is required.")
    private UUID categoryId;
}
