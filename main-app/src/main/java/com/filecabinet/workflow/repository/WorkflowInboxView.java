package com.filecabinet.workflow.repository;

import com.filecabinet.workflow.model.WorkflowStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public interface WorkflowInboxView {

    UUID getId();

    UUID getDocumentId();

    String getDocumentTitle();

    String getInitiatorUsername();

    WorkflowStatus getStatus();

    LocalDateTime getCreatedOn();
}
