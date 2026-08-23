package com.filecabinet.extraction.job.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(indexes = {
        @Index(name = "idx_extracted_field_job", columnList = "job_id")
})
public class ExtractedField {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private ExtractionJob job;

    @Column(nullable = false, length = 100)
    private String fieldName;

    @Column(length = 1000)
    private String fieldValue;

    @Column(nullable = false)
    private double confidence;

    private Integer boxPage;

    private Double boxX;

    private Double boxY;

    private Double boxWidth;

    private Double boxHeight;

    private Double boxPageWidth;

    private Double boxPageHeight;
}