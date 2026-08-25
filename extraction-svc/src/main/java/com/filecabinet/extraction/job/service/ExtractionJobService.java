package com.filecabinet.extraction.job.service;

import com.filecabinet.extraction.ai.ExtractedFieldData;
import com.filecabinet.extraction.ai.ExtractionResult;
import com.filecabinet.extraction.ai.ExtractorPort;
import com.filecabinet.extraction.ai.FieldBox;
import com.filecabinet.extraction.ai.LineItemData;
import com.filecabinet.extraction.job.model.ExtractedField;
import com.filecabinet.extraction.job.model.ExtractedLineItem;
import com.filecabinet.extraction.job.model.ExtractionJob;
import com.filecabinet.extraction.job.model.ExtractionStatus;
import com.filecabinet.extraction.job.repository.ExtractedFieldRepository;
import com.filecabinet.extraction.job.repository.ExtractedLineItemRepository;
import com.filecabinet.extraction.job.repository.ExtractionJobRepository;
import com.filecabinet.extraction.shared.exception.ExtractionExceptions.ExtractionFailedException;
import com.filecabinet.extraction.shared.exception.ExtractionExceptions.JobNotFoundException;
import com.filecabinet.extraction.web.dto.CreateExtractionRequest;
import com.filecabinet.extraction.web.dto.ExtractedFieldResponse;
import com.filecabinet.extraction.web.dto.ExtractionJobResponse;
import com.filecabinet.extraction.web.dto.FieldBoxResponse;
import com.filecabinet.extraction.web.dto.LineItemResponse;
import com.filecabinet.extraction.web.dto.UpdateLineItemRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractionJobService {

    private final ExtractionJobRepository jobRepository;
    private final ExtractedFieldRepository fieldRepository;
    private final ExtractedLineItemRepository lineItemRepository;
    private final ExtractorPort extractor;
    private final InvoiceSanityChecker sanityChecker;

    @Transactional
    public ExtractionJobResponse queue(CreateExtractionRequest request) {
        ExtractionJob job = ExtractionJob.builder()
                .documentId(request.documentId())
                .sourcePath(request.sourcePath())
                .provider(extractor.providerName())
                .status(ExtractionStatus.QUEUED)
                .attempts(0)
                .requestedOn(LocalDateTime.now())
                .build();
        job = jobRepository.save(job);
        log.info("Queued extraction job {} for document {}", job.getId(), job.getDocumentId());
        return toResponse(job, List.of(), List.of());
    }

    @Transactional
    public ExtractionJobResponse reprocess(UUID jobId) {
        ExtractionJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException("No extraction job with id " + jobId));
        fieldRepository.deleteByJobId(jobId);
        lineItemRepository.deleteByJobId(jobId);
        job.setStatus(ExtractionStatus.QUEUED);
        job.setCompletedOn(null);
        job = jobRepository.save(job);
        log.info("Reset extraction job {} for reprocessing", jobId);
        return toResponse(job, List.of(), List.of());
    }

    @Transactional
    public void delete(UUID jobId) {
        if (!jobRepository.existsById(jobId)) {
            throw new JobNotFoundException("No extraction job with id " + jobId);
        }
        fieldRepository.deleteByJobId(jobId);
        lineItemRepository.deleteByJobId(jobId);
        jobRepository.deleteById(jobId);
        log.info("Deleted extraction job {}", jobId);
    }

    @Transactional(readOnly = true)
    public ExtractionJobResponse getByDocument(UUID documentId) {
        ExtractionJob job = jobRepository.findFirstByDocumentIdOrderByRequestedOnDesc(documentId)
                .orElseThrow(() -> new JobNotFoundException("No extraction job for document " + documentId));
        return toResponse(job, fieldRepository.findByJobIdOrderByFieldName(job.getId()),
                lineItemRepository.findByJobIdOrderByLineNumber(job.getId()));
    }

    @Transactional(readOnly = true)
    public List<UUID> findQueuedJobIds() {
        return jobRepository.findByStatusOrderByRequestedOnAsc(ExtractionStatus.QUEUED).stream()
                .map(ExtractionJob::getId)
                .toList();
    }

    @Transactional
    public void processJob(UUID jobId) {
        ExtractionJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() != ExtractionStatus.QUEUED) {
            return;
        }
        job.setStatus(ExtractionStatus.PROCESSING);
        job.setAttempts(job.getAttempts() + 1);
        job.setProcessingStartedOn(LocalDateTime.now());
        jobRepository.save(job);

        try {
            byte[] bytes = readFile(job.getSourcePath());
            String filename = Path.of(job.getSourcePath()).getFileName().toString();
            ExtractionResult result = extractor.extract(bytes, filename);

            for (ExtractedFieldData data : result.fields()) {
                ExtractedField.ExtractedFieldBuilder builder = ExtractedField.builder()
                        .job(job)
                        .fieldName(data.name())
                        .fieldValue(data.value())
                        .confidence(data.confidence());
                FieldBox box = data.box();
                if (box != null) {
                    builder.boxPage(box.page()).boxX(box.x()).boxY(box.y())
                            .boxWidth(box.width()).boxHeight(box.height())
                            .boxPageWidth(box.pageWidth()).boxPageHeight(box.pageHeight());
                }
                fieldRepository.save(builder.build());
            }

            for (LineItemData item : result.lineItems()) {
                ExtractedLineItem.ExtractedLineItemBuilder builder = ExtractedLineItem.builder()
                        .job(job)
                        .lineNumber(item.lineNumber())
                        .description(item.description())
                        .quantity(item.quantity())
                        .unitPrice(item.unitPrice())
                        .vatRatePercent(item.vatRatePercent())
                        .totalAmount(item.totalAmount())
                        .category(item.category());
                FieldBox box = item.box();
                if (box != null) {
                    builder.boxPage(box.page()).boxX(box.x()).boxY(box.y())
                            .boxWidth(box.width()).boxHeight(box.height())
                            .boxPageWidth(box.pageWidth()).boxPageHeight(box.pageHeight());
                }
                lineItemRepository.save(builder.build());
            }

            List<String> warnings = sanityChecker.check(result.fields(), result.lineItems());
            job.setNeedsReview(!warnings.isEmpty());
            job.setReviewNotes(warnings.isEmpty() ? null : String.join("\n", warnings));
            job.setStatus(ExtractionStatus.COMPLETED);
            job.setCompletedOn(LocalDateTime.now());
            jobRepository.save(job);
            log.info("Completed extraction job {} with {} fields, {} line items, {} review flag(s)",
                    jobId, result.fields().size(), result.lineItems().size(), warnings.size());
        } catch (RuntimeException ex) {
            job.setStatus(ExtractionStatus.FAILED);
            jobRepository.save(job);
            log.error("Extraction job {} failed: {}", jobId, ex.getMessage());
        }
    }

    @Transactional
    public ExtractionJobResponse updateFieldValue(UUID documentId, UUID fieldId, String value) {
        ExtractionJob job = jobRepository.findFirstByDocumentIdOrderByRequestedOnDesc(documentId)
                .orElseThrow(() -> new JobNotFoundException("No extraction job for document " + documentId));
        ExtractedField field = fieldRepository.findById(fieldId)
                .orElseThrow(() -> new JobNotFoundException("No extracted field with id " + fieldId));
        if (!field.getJob().getId().equals(job.getId())) {
            throw new JobNotFoundException("No extracted field with id " + fieldId);
        }
        field.setFieldValue(value);
        fieldRepository.save(field);
        log.info("Updated extracted field {} on document {}", fieldId, documentId);
        return toResponse(job, fieldRepository.findByJobIdOrderByFieldName(job.getId()),
                lineItemRepository.findByJobIdOrderByLineNumber(job.getId()));
    }

    @Transactional
    public int purgeCompletedOlderThan(LocalDateTime cutoff) {
        List<ExtractionJob> old = jobRepository.findByStatusAndCompletedOnBefore(ExtractionStatus.COMPLETED, cutoff);
        for (ExtractionJob job : old) {
            fieldRepository.deleteByJobId(job.getId());
            lineItemRepository.deleteByJobId(job.getId());
            jobRepository.deleteById(job.getId());
        }
        if (!old.isEmpty()) {
            log.info("Purged {} completed extraction jobs older than {}", old.size(), cutoff);
        }
        return old.size();
    }

    @Transactional
    public int resetStuckProcessing(LocalDateTime cutoff) {
        List<ExtractionJob> stuck = jobRepository.findByStatusAndProcessingStartedOnBefore(ExtractionStatus.PROCESSING, cutoff);
        for (ExtractionJob job : stuck) {
            job.setStatus(ExtractionStatus.QUEUED);
            jobRepository.save(job);
        }
        if (!stuck.isEmpty()) {
            log.info("Requeued {} stuck extraction jobs", stuck.size());
        }
        return stuck.size();
    }

    private byte[] readFile(String sourcePath) {
        try {
            return Files.readAllBytes(Path.of(sourcePath));
        } catch (IOException e) {
            throw new ExtractionFailedException("Could not read source file: " + sourcePath, e);
        }
    }

    private ExtractionJobResponse toResponse(ExtractionJob job, List<ExtractedField> fields, List<ExtractedLineItem> lineItems) {
        List<ExtractedFieldResponse> fieldResponses = fields.stream().map(this::toFieldResponse).toList();
        List<LineItemResponse> lineItemResponses = lineItems.stream().map(this::toLineItemResponse).toList();
        List<String> reviewNotes = (job.getReviewNotes() == null || job.getReviewNotes().isBlank())
                ? List.of()
                : List.of(job.getReviewNotes().split("\n"));
        return new ExtractionJobResponse(
                job.getId(),
                job.getDocumentId(),
                job.getProvider(),
                job.getStatus().name(),
                job.getAttempts(),
                job.getRequestedOn(),
                job.getCompletedOn(),
                job.isNeedsReview(),
                reviewNotes,
                fieldResponses,
                lineItemResponses);
    }

    private ExtractedFieldResponse toFieldResponse(ExtractedField field) {
        FieldBoxResponse box = field.getBoxPage() == null ? null
                : new FieldBoxResponse(field.getBoxPage(), field.getBoxX(), field.getBoxY(),
                field.getBoxWidth(), field.getBoxHeight(), field.getBoxPageWidth(), field.getBoxPageHeight());
        return new ExtractedFieldResponse(field.getId(), field.getFieldName(), field.getFieldValue(),
                field.getConfidence(), box);
    }

    private LineItemResponse toLineItemResponse(ExtractedLineItem item) {
        FieldBoxResponse box = item.getBoxPage() == null ? null
                : new FieldBoxResponse(item.getBoxPage(), item.getBoxX(), item.getBoxY(),
                item.getBoxWidth(), item.getBoxHeight(), item.getBoxPageWidth(), item.getBoxPageHeight());
        return new LineItemResponse(item.getId(), item.getLineNumber(), item.getDescription(), item.getQuantity(),
                item.getUnitPrice(), item.getVatRatePercent(), item.getTotalAmount(), item.getCategory(), box);
    }

    @Transactional
    public ExtractionJobResponse updateLineItem(UUID documentId, UUID lineItemId, UpdateLineItemRequest request) {
        ExtractionJob job = jobRepository.findFirstByDocumentIdOrderByRequestedOnDesc(documentId)
                .orElseThrow(() -> new JobNotFoundException("No extraction job for document " + documentId));
        ExtractedLineItem item = lineItemRepository.findById(lineItemId)
                .orElseThrow(() -> new JobNotFoundException("No line item with id " + lineItemId));
        if (!item.getJob().getId().equals(job.getId())) {
            throw new JobNotFoundException("No line item with id " + lineItemId);
        }
        item.setLineNumber(request.lineNumber());
        item.setDescription(request.description());
        item.setQuantity(request.quantity());
        item.setUnitPrice(request.unitPrice());
        item.setVatRatePercent(request.vatRatePercent());
        item.setTotalAmount(request.totalAmount());
        item.setCategory(request.category());
        lineItemRepository.save(item);
        log.info("Updated line item {} on document {}", lineItemId, documentId);
        return toResponse(job, fieldRepository.findByJobIdOrderByFieldName(job.getId()),
                lineItemRepository.findByJobIdOrderByLineNumber(job.getId()));
    }
}
