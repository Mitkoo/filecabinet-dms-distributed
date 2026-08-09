package com.filecabinet.workflow.repository;

import com.filecabinet.workflow.model.ReviewStep;
import com.filecabinet.workflow.model.StepStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReviewStepRepository extends JpaRepository<ReviewStep, UUID> {

    List<ReviewStep> findByWorkflowIdOrderByStepOrderAsc(UUID workflowId);

    List<ReviewStep> findByReviewerIdAndStatus(UUID reviewerId, StepStatus status);
}
