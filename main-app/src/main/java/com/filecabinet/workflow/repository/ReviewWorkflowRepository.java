package com.filecabinet.workflow.repository;

import com.filecabinet.workflow.model.ReviewWorkflow;
import com.filecabinet.workflow.model.StepStatus;
import com.filecabinet.workflow.model.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewWorkflowRepository extends JpaRepository<ReviewWorkflow, UUID> {

    Optional<ReviewWorkflow> findFirstByDocumentIdOrderByCreatedOnDesc(UUID documentId);

    @Query("""
            SELECT COUNT(s)
            FROM ReviewStep s
            JOIN s.workflow w
            WHERE s.reviewer.id = :userId
              AND s.status = :pending
              AND w.status = :inProgress
              AND s.stepOrder = (SELECT MIN(s2.stepOrder) FROM ReviewStep s2
                                 WHERE s2.workflow = w AND s2.status = :pending)
            """)
    long countActionable(@Param("userId") UUID userId,
                         @Param("pending") StepStatus pending,
                         @Param("inProgress") WorkflowStatus inProgress);

    @Query("""
            SELECT w.id FROM ReviewWorkflow w
            WHERE (w.initiator.id = :userId
                   OR EXISTS (SELECT 1 FROM ReviewStep s
                              WHERE s.workflow = w AND s.reviewer.id = :userId))
              AND NOT EXISTS (SELECT 1 FROM WorkflowRead r
                              WHERE r.workflow = w AND r.reader.id = :userId)
            """)
    List<UUID> findParticipantWorkflowIdsWithoutRead(@Param("userId") UUID userId);

    @Query("""
            SELECT CASE WHEN COUNT(w) > 0 THEN TRUE ELSE FALSE END
            FROM ReviewWorkflow w
            WHERE w.document.id = :documentId
              AND (w.initiator.id = :userId
                   OR EXISTS (SELECT 1 FROM ReviewStep s
                              WHERE s.workflow = w AND s.reviewer.id = :userId))
            """)
    boolean hasInvolvement(@Param("documentId") UUID documentId, @Param("userId") UUID userId);

    default long countActionableForUser(UUID userId) {
        return countActionable(userId, StepStatus.PENDING, WorkflowStatus.IN_PROGRESS);
    }
}
