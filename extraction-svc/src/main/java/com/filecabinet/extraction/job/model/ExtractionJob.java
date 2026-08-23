package com.filecabinet.extraction.job.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(indexes = {
        @Index(name = "idx_extraction_job_document", columnList = "documentId"),
        @Index(name = "idx_extraction_job_status", columnList = "status")
})
public class ExtractionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID documentId;

    @Column(nullable = false, length = 255)
    private String sourcePath;

    @Column(nullable = false, length = 50)
    private String provider;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ExtractionStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private LocalDateTime requestedOn;

    private LocalDateTime completedOn;
}
