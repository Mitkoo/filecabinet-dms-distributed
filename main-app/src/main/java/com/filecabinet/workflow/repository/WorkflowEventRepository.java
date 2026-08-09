package com.filecabinet.workflow.repository;

import com.filecabinet.workflow.model.WorkflowEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowEventRepository extends JpaRepository<WorkflowEvent, UUID> {

    List<WorkflowEvent> findByWorkflowIdOrderByCreatedOnAsc(UUID workflowId);
}
