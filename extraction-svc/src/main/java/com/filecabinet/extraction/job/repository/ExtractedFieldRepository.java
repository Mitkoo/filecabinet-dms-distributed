package com.filecabinet.extraction.job.repository;

import com.filecabinet.extraction.job.model.ExtractedField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface ExtractedFieldRepository extends JpaRepository<ExtractedField, UUID> {

    List<ExtractedField> findByJobIdOrderByFieldName(UUID jobId);

    @Modifying
    @Transactional
    void deleteByJobId(UUID jobId);
}
