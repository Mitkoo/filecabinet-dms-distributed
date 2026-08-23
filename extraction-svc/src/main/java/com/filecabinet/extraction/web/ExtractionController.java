package com.filecabinet.extraction.web;

import com.filecabinet.extraction.job.service.ExtractionJobService;
import com.filecabinet.extraction.web.dto.CreateExtractionRequest;
import com.filecabinet.extraction.web.dto.ExtractionJobResponse;
import com.filecabinet.extraction.web.dto.UpdateFieldValueRequest;
import com.filecabinet.extraction.web.dto.UpdateLineItemRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/extractions")
@RequiredArgsConstructor
public class ExtractionController {

    private final ExtractionJobService jobService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ExtractionJobResponse queue(@Valid @RequestBody CreateExtractionRequest request) {
        return jobService.queue(request);
    }

    @PutMapping("/{id}/reprocess")
    public ExtractionJobResponse reprocess(@PathVariable UUID id) {
        return jobService.reprocess(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        jobService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/by-document/{documentId}")
    public ExtractionJobResponse getByDocument(@PathVariable UUID documentId) {
        return jobService.getByDocument(documentId);
    }

    @PutMapping("/by-document/{documentId}/fields/{fieldId}")
    public ExtractionJobResponse updateField(@PathVariable UUID documentId,
                                             @PathVariable UUID fieldId,
                                             @Valid @RequestBody UpdateFieldValueRequest request) {
        return jobService.updateFieldValue(documentId, fieldId, request.fieldValue());
    }

    @PutMapping("/by-document/{documentId}/line-items/{lineItemId}")
    public ExtractionJobResponse updateLineItem(@PathVariable UUID documentId,
                                                @PathVariable UUID lineItemId,
                                                @RequestBody UpdateLineItemRequest request) {
        return jobService.updateLineItem(documentId, lineItemId, request);
    }
}
