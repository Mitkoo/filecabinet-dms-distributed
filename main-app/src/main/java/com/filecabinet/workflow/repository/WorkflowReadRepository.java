package com.filecabinet.workflow.repository;

import com.filecabinet.workflow.model.WorkflowRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowReadRepository extends JpaRepository<WorkflowRead, UUID> {

    Optional<WorkflowRead> findByWorkflowIdAndReaderId(UUID workflowId, UUID readerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE WorkflowRead r SET r.lastReadOn = :readOn WHERE r.reader.id = :readerId")
    int touchAllForReader(@Param("readerId") UUID readerId, @Param("readOn") LocalDateTime readOn);
}
