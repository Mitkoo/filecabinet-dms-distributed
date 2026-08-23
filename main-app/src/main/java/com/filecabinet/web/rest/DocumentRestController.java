package com.filecabinet.web.rest;

import com.filecabinet.document.model.Document;
import com.filecabinet.document.model.DocumentField;
import com.filecabinet.document.model.DocumentType;
import com.filecabinet.document.repository.DocumentListView;
import com.filecabinet.document.service.DocumentService;
import com.filecabinet.document.service.FileStorageService;
import com.filecabinet.integration.ExtractionClient;
import com.filecabinet.integration.dto.ExtractionFieldDto;
import com.filecabinet.integration.dto.ExtractionJobDto;
import com.filecabinet.integration.dto.QueueExtractionRequest;
import com.filecabinet.integration.dto.UpdateFieldRequest;
import com.filecabinet.integration.dto.UpdateLineItemRequest;
import com.filecabinet.web.rest.dto.ApplyFieldsRequest;
import com.filecabinet.web.rest.dto.FieldValueUpdateRequest;
import com.filecabinet.web.rest.dto.LineItemUpdateRequest;
import com.filecabinet.shared.exception.ServiceExceptions.InvalidStateException;
import com.filecabinet.shared.security.AppUserDetails;
import com.filecabinet.web.rest.dto.DocumentDetailResponse;
import com.filecabinet.web.rest.dto.DocumentResponse;
import com.filecabinet.web.rest.dto.DocumentUpdateRequest;
import com.filecabinet.web.rest.dto.FieldRequest;
import com.filecabinet.web.rest.dto.FieldResponse;
import com.filecabinet.web.rest.dto.PagedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentRestController {

    private final DocumentService documentService;
    private final FileStorageService fileStorageService;
    private final ExtractionClient extractionClient;

    @GetMapping
    public PagedResponse<DocumentResponse> list(@AuthenticationPrincipal AppUserDetails principal,
                                                @RequestParam(defaultValue = "mine") String scope,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        boolean all = "all".equalsIgnoreCase(scope);
        Page<DocumentListView> result = documentService.list(principal.getId(), all,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "uploadedOn")));
        List<DocumentResponse> content = result.getContent().stream().map(this::toListResponse).toList();
        return new PagedResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/{id}")
    public DocumentDetailResponse detail(@PathVariable UUID id) {
        Document document = documentService.findDetailById(id);
        return toDetailResponse(document, documentService.findFields(id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentDetailResponse upload(@AuthenticationPrincipal AppUserDetails principal,
                                         @RequestParam String title,
                                         @RequestParam String documentType,
                                         @RequestParam UUID categoryId,
                                         @RequestParam("file") MultipartFile file) {
        DocumentType type = parseType(documentType);
        String storedPath = fileStorageService.store(file);
        Document document = documentService.create(title, type, storedPath, principal.getId(), categoryId);
        log.info("User {} uploaded document {}", principal.getUsername(), document.getId());

        if (type == DocumentType.INVOICE) {
            queueExtraction(document.getId(), storedPath);
        }
        return toDetailResponse(documentService.findDetailById(document.getId()),
                documentService.findFields(document.getId()));
    }

    @PutMapping("/{id}")
    public DocumentDetailResponse update(@PathVariable UUID id, @Valid @RequestBody DocumentUpdateRequest request) {
        DocumentType type = parseType(request.documentType());
        Document existing = documentService.findById(id);
        documentService.update(id, request.title(), type, existing.getFilePath(), request.categoryId());
        return toDetailResponse(documentService.findDetailById(id), documentService.findFields(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documentService.delete(id);
        deleteExtraction(id);
        log.info("Deleted document {}", id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/fields")
    @ResponseStatus(HttpStatus.CREATED)
    public FieldResponse addField(@PathVariable UUID id, @Valid @RequestBody FieldRequest request) {
        DocumentField field = documentService.addField(id, request.fieldName(), request.fieldValue());
        return toFieldResponse(field);
    }

    @DeleteMapping("/{id}/fields/{fieldId}")
    public ResponseEntity<Void> removeField(@PathVariable UUID id, @PathVariable UUID fieldId) {
        documentService.removeField(id, fieldId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/mark-paid")
    public DocumentDetailResponse markPaid(@PathVariable UUID id) {
        documentService.markPaid(id);
        log.info("Document {} marked as paid", id);
        return toDetailResponse(documentService.findDetailById(id), documentService.findFields(id));
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        Document document = documentService.findById(id);
        Resource resource = fileStorageService.loadAsResource(document.getFilePath());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @PostMapping("/{id}/extract")
    public ExtractionJobDto extract(@PathVariable UUID id) {
        Document document = documentService.findById(id);
        return extractionClient.queue(new QueueExtractionRequest(id, document.getFilePath()));
    }

    @GetMapping("/{id}/extraction")
    public ExtractionJobDto extraction(@PathVariable UUID id) {
        return extractionClient.getByDocument(id);
    }

    @PutMapping("/{id}/extraction/fields/{fieldId}")
    public ExtractionJobDto updateExtractionField(@PathVariable UUID id, @PathVariable UUID fieldId,
                                                  @Valid @RequestBody FieldValueUpdateRequest request) {
        log.info("Correcting extracted field {} on document {}", fieldId, id);
        return extractionClient.updateField(id, fieldId, new UpdateFieldRequest(request.fieldValue()));
    }

    @PutMapping("/{id}/extraction/line-items/{lineItemId}")
    public ExtractionJobDto updateExtractionLineItem(@PathVariable UUID id, @PathVariable UUID lineItemId,
                                                     @RequestBody LineItemUpdateRequest request) {
        return extractionClient.updateLineItem(id, lineItemId, new UpdateLineItemRequest(
                request.lineNumber(), request.description(), request.quantity(), request.unitPrice(),
                request.vatRatePercent(), request.totalAmount(), request.category()));
    }

    @PostMapping("/{id}/apply-extraction")
    public DocumentDetailResponse applyExtraction(@PathVariable UUID id) {
        ExtractionJobDto extraction = extractionClient.getByDocument(id);
        Map<String, String> fields = new LinkedHashMap<>();
        if (extraction != null && extraction.fields() != null) {
            for (ExtractionFieldDto field : extraction.fields()) {
                if (!"requires_manual_review".equals(field.fieldName())) {
                    fields.put(field.fieldName(), field.fieldValue());
                }
            }
        }
        documentService.applyReviewedFields(id, fields);
        log.info("Applied {} reviewed fields to document {}", fields.size(), id);
        return toDetailResponse(documentService.findDetailById(id), documentService.findFields(id));
    }

    @PutMapping("/{id}/fields")
    public DocumentDetailResponse setFields(@PathVariable UUID id, @RequestBody ApplyFieldsRequest request) {
        Map<String, String> fields = request.fields() == null ? Map.of() : request.fields();
        documentService.applyReviewedFields(id, fields);
        log.info("Saved {} reviewed header fields to document {}", fields.size(), id);
        return toDetailResponse(documentService.findDetailById(id), documentService.findFields(id));
    }

    private void queueExtraction(UUID documentId, String path) {
        try {
            extractionClient.queue(new QueueExtractionRequest(documentId, path));
            log.info("Queued extraction for document {}", documentId);
        } catch (RuntimeException ex) {
            log.warn("Could not queue extraction for document {}: {}", documentId, ex.getMessage());
        }
    }

    private void deleteExtraction(UUID documentId) {
        try {
            ExtractionJobDto job = extractionClient.getByDocument(documentId);
            if (job != null) {
                extractionClient.delete(job.id());
            }
        } catch (RuntimeException ex) {
            log.warn("No extraction job to delete for document {}", documentId);
        }
    }

    private DocumentType parseType(String value) {
        try {
            return DocumentType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidStateException("Unknown document type: " + value);
        }
    }

    private DocumentResponse toListResponse(DocumentListView view) {
        return new DocumentResponse(view.getId(), view.getTitle(), view.getDocumentType().name(),
                view.getStatus().name(), view.getUploadedOn(), view.getCategoryName(), view.getOwnerUsername());
    }

    private DocumentDetailResponse toDetailResponse(Document document, List<DocumentField> fields) {
        List<FieldResponse> fieldResponses = fields.stream().map(this::toFieldResponse).toList();
        return new DocumentDetailResponse(
                document.getId(), document.getTitle(), document.getDocumentType().name(),
                document.getStatus().name(), document.getFilePath(), document.getUploadedOn(),
                document.getCategory().getId(), document.getCategory().getName(),
                document.getOwner().getUsername(), fieldResponses);
    }

    private FieldResponse toFieldResponse(DocumentField field) {
        return new FieldResponse(field.getId(), field.getFieldName(), field.getFieldValue(), field.getConfidence());
    }
}
