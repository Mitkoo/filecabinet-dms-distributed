package com.filecabinet.extraction.job.repository;

import com.filecabinet.extraction.job.model.ExtractedLineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface ExtractedLineItemRepository extends JpaRepository<ExtractedLineItem, UUID> {

    List<ExtractedLineItem> findByJobIdOrderByLineNumber(UUID jobId);

    @Modifying
    @Transactional
    void deleteByJobId(UUID jobId);
}
