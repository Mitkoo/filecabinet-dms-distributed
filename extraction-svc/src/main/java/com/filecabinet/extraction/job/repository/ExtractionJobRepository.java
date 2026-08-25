package com.filecabinet.extraction.job.repository;

import com.filecabinet.extraction.job.model.ExtractionJob;
import com.filecabinet.extraction.job.model.ExtractionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExtractionJobRepository extends JpaRepository<ExtractionJob, UUID> {

    List<ExtractionJob> findByStatusOrderByRequestedOnAsc(ExtractionStatus status);

    Optional<ExtractionJob> findFirstByDocumentIdOrderByRequestedOnDesc(UUID documentId);

    List<ExtractionJob> findByStatusAndProcessingStartedOnBefore(ExtractionStatus status, LocalDateTime cutoff);

    List<ExtractionJob> findByStatusAndCompletedOnBefore(ExtractionStatus status, LocalDateTime cutoff);
}
