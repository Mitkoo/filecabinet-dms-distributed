package com.filecabinet.workflow.repository;

import com.filecabinet.workflow.model.WorkflowEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowEventRepository extends JpaRepository<WorkflowEvent, UUID> {

    @EntityGraph(attributePaths = "actor")
    List<WorkflowEvent> findByWorkflowIdOrderByCreatedOnAsc(UUID workflowId);

    @Query("""
            SELECT e FROM WorkflowEvent e
            JOIN e.workflow w
            LEFT JOIN WorkflowRead r ON r.workflow = w AND r.reader.id = :userId
            WHERE (e.actor IS NULL OR e.actor.id <> :userId)
              AND (w.initiator.id = :userId
                   OR EXISTS (SELECT 1 FROM ReviewStep s
                              WHERE s.workflow = w AND s.reviewer.id = :userId))
              AND (r.lastReadOn IS NULL OR e.createdOn > r.lastReadOn)
            ORDER BY e.createdOn DESC
            """)
    List<WorkflowEvent> findUnreadForUser(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            SELECT COUNT(e) FROM WorkflowEvent e
            JOIN e.workflow w
            LEFT JOIN WorkflowRead r ON r.workflow = w AND r.reader.id = :userId
            WHERE (e.actor IS NULL OR e.actor.id <> :userId)
              AND (w.initiator.id = :userId
                   OR EXISTS (SELECT 1 FROM ReviewStep s
                              WHERE s.workflow = w AND s.reviewer.id = :userId))
              AND (r.lastReadOn IS NULL OR e.createdOn > r.lastReadOn)
            """)
    long countUnreadForUser(@Param("userId") UUID userId);
}
