package com.filecabinet.document.repository;

import com.filecabinet.document.model.DocumentStatus;
import com.filecabinet.document.model.DocumentType;

import java.time.LocalDateTime;
import java.util.UUID;

public interface DocumentListView {

    UUID getId();

    String getTitle();

    DocumentType getDocumentType();

    DocumentStatus getStatus();

    LocalDateTime getUploadedOn();

    String getCategoryName();

    String getOwnerUsername();
}
