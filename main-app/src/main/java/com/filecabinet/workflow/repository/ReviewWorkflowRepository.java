package com.filecabinet.workflow.repository;

import com.filecabinet.workflow.model.ReviewWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewWorkflowRepository extends JpaRepository<ReviewWorkflow, UUID> {

    List<ReviewWorkflow> findByDocumentIdOrderByCreatedOnDesc(UUID documentId);
}
