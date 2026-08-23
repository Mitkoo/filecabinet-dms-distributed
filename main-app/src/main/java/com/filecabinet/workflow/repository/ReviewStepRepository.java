package com.filecabinet.workflow.repository;

import com.filecabinet.workflow.model.ReviewStep;
import com.filecabinet.workflow.model.StepStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewStepRepository extends JpaRepository<ReviewStep, UUID> {

    @EntityGraph(attributePaths = "reviewer")
    List<ReviewStep> findByWorkflowIdOrderByStepOrderAsc(UUID workflowId);

    List<ReviewStep> findByReviewerIdAndStatus(UUID reviewerId, StepStatus status);

    Optional<ReviewStep> findFirstByWorkflowIdAndStatusOrderByStepOrderAsc(UUID workflowId, StepStatus status);

    boolean existsByWorkflowIdAndReviewerId(UUID workflowId, UUID reviewerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE ReviewStep s
            SET s.status = :skipped
            WHERE s.workflow.id = :workflowId AND s.status = :pending
            """)
    int updatePendingStatus(@Param("workflowId") UUID workflowId,
                            @Param("pending") StepStatus pending,
                            @Param("skipped") StepStatus skipped);

    default Optional<ReviewStep> findCurrentStep(UUID workflowId) {
        return findFirstByWorkflowIdAndStatusOrderByStepOrderAsc(workflowId, StepStatus.PENDING);
    }

    default int skipRemainingSteps(UUID workflowId) {
        return updatePendingStatus(workflowId, StepStatus.PENDING, StepStatus.SKIPPED);
    }
}
