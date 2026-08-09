package com.filecabinet.workflow.repository;

import com.filecabinet.workflow.model.WorkflowRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowReadRepository extends JpaRepository<WorkflowRead, UUID> {

    Optional<WorkflowRead> findByWorkflowIdAndReaderId(UUID workflowId, UUID readerId);
}
