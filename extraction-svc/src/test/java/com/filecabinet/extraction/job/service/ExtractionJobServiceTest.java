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
import com.filecabinet.extraction.web.dto.ExtractionJobResponse;
import com.filecabinet.extraction.web.dto.UpdateLineItemRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtractionJobServiceTest {

    @Mock
    private ExtractionJobRepository jobRepository;
    @Mock
    private ExtractedFieldRepository fieldRepository;
    @Mock
    private ExtractedLineItemRepository lineItemRepository;
    @Mock
    private ExtractorPort extractor;
    @Mock
    private InvoiceSanityChecker sanityChecker;

    @InjectMocks
    private ExtractionJobService service;

    private ExtractionJob queuedJob(UUID id, String sourcePath) {
        return ExtractionJob.builder()
                .id(id)
                .documentId(UUID.randomUUID())
                .sourcePath(sourcePath)
                .provider("mistral")
                .status(ExtractionStatus.QUEUED)
                .attempts(0)
                .requestedOn(LocalDateTime.now())
                .build();
    }

    @Test
    void queueCreatesJobInQueuedState() {
        when(extractor.providerName()).thenReturn("mistral");
        when(jobRepository.save(any(ExtractionJob.class))).thenAnswer(inv -> inv.getArgument(0));

        ExtractionJobResponse response = service.queue(new CreateExtractionRequest(UUID.randomUUID(), "C:/x.pdf"));

        assertThat(response.status()).isEqualTo("QUEUED");
        assertThat(response.provider()).isEqualTo("mistral");
        assertThat(response.fields()).isEmpty();
        assertThat(response.lineItems()).isEmpty();
    }

    @Test
    void reprocessResetsStatusAndClearsFieldsAndLineItems() {
        UUID id = UUID.randomUUID();
        ExtractionJob job = queuedJob(id, "C:/x.pdf");
        job.setStatus(ExtractionStatus.COMPLETED);
        job.setCompletedOn(LocalDateTime.now());
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(ExtractionJob.class))).thenAnswer(inv -> inv.getArgument(0));

        ExtractionJobResponse response = service.reprocess(id);

        verify(fieldRepository).deleteByJobId(id);
        verify(lineItemRepository).deleteByJobId(id);
        assertThat(response.status()).isEqualTo("QUEUED");
        assertThat(job.getCompletedOn()).isNull();
    }

    @Test
    void reprocessUnknownJobThrows() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.reprocess(id)).isInstanceOf(JobNotFoundException.class);
    }

    @Test
    void deleteRemovesFieldsLineItemsAndJob() {
        UUID id = UUID.randomUUID();
        when(jobRepository.existsById(id)).thenReturn(true);
        service.delete(id);
        verify(fieldRepository).deleteByJobId(id);
        verify(lineItemRepository).deleteByJobId(id);
        verify(jobRepository).deleteById(id);
    }

    @Test
    void deleteUnknownJobThrows() {
        UUID id = UUID.randomUUID();
        when(jobRepository.existsById(id)).thenReturn(false);
        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(JobNotFoundException.class);
    }

    @Test
    void getByDocumentUnknownThrows() {
        UUID docId = UUID.randomUUID();
        when(jobRepository.findFirstByDocumentIdOrderByRequestedOnDesc(docId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getByDocument(docId)).isInstanceOf(JobNotFoundException.class);
    }

    @Test
    void processJobExtractsFieldsLineItemsAndCompletes(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("invoice.pdf");
        Files.write(file, new byte[]{1, 2, 3});
        UUID id = UUID.randomUUID();
        ExtractionJob job = queuedJob(id, file.toString());
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(ExtractionJob.class))).thenAnswer(inv -> inv.getArgument(0));
        List<ExtractedFieldData> fields = List.of(
                new ExtractedFieldData("total_net", "100.00", 1.0, new FieldBox(1, 10, 20, 30, 8, 595, 842)),
                new ExtractedFieldData("currency", "EUR", 1.0));
        List<LineItemData> lineItems = List.of(
                new LineItemData(1, "Widget", 2.0, 5.0, 20.0, 10.0, "Hardware"));
        when(extractor.extract(any(), any())).thenReturn(new ExtractionResult(fields, lineItems));

        service.processJob(id);

        assertThat(job.getStatus()).isEqualTo(ExtractionStatus.COMPLETED);
        assertThat(job.getAttempts()).isEqualTo(1);
        ArgumentCaptor<ExtractedField> fieldCaptor = ArgumentCaptor.forClass(ExtractedField.class);
        verify(fieldRepository, times(2)).save(fieldCaptor.capture());
        ExtractedField withBox = fieldCaptor.getAllValues().get(0);
        assertThat(withBox.getBoxPage()).isEqualTo(1);
        assertThat(withBox.getBoxX()).isEqualTo(10.0);
        ArgumentCaptor<ExtractedLineItem> lineCaptor = ArgumentCaptor.forClass(ExtractedLineItem.class);
        verify(lineItemRepository, times(1)).save(lineCaptor.capture());
        assertThat(lineCaptor.getValue().getDescription()).isEqualTo("Widget");
        assertThat(lineCaptor.getValue().getTotalAmount()).isEqualTo(10.0);
    }

    @Test
    void processJobMarksFailedWhenExtractorThrows(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("invoice.pdf");
        Files.write(file, new byte[]{1});
        UUID id = UUID.randomUUID();
        ExtractionJob job = queuedJob(id, file.toString());
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(ExtractionJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(extractor.extract(any(), any())).thenThrow(new ExtractionFailedException("boom"));

        service.processJob(id);

        assertThat(job.getStatus()).isEqualTo(ExtractionStatus.FAILED);
    }

    @Test
    void processJobSkipsNonQueuedJob() {
        UUID id = UUID.randomUUID();
        ExtractionJob job = queuedJob(id, "C:/x.pdf");
        job.setStatus(ExtractionStatus.COMPLETED);
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));

        service.processJob(id);

        verify(extractor, never()).extract(any(), any());
    }

    @Test
    void resetStuckProcessingRequeuesJobsPastTheCutoff() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        ExtractionJob stuck = queuedJob(UUID.randomUUID(), "C:/x.pdf");
        stuck.setStatus(ExtractionStatus.PROCESSING);
        when(jobRepository.findByStatusAndProcessingStartedOnBefore(ExtractionStatus.PROCESSING, cutoff))
                .thenReturn(List.of(stuck));
        when(jobRepository.save(any(ExtractionJob.class))).thenAnswer(inv -> inv.getArgument(0));

        int requeued = service.resetStuckProcessing(cutoff);

        assertThat(requeued).isEqualTo(1);
        assertThat(stuck.getStatus()).isEqualTo(ExtractionStatus.QUEUED);
    }

    @Test
    void updateFieldValueChangesTheStoredValue() {
        UUID docId = UUID.randomUUID();
        UUID fieldId = UUID.randomUUID();
        ExtractionJob job = queuedJob(UUID.randomUUID(), "C:/x.pdf");
        ExtractedField field = ExtractedField.builder()
                .id(fieldId).job(job).fieldName("currency").fieldValue("EUR").confidence(1.0).build();
        when(jobRepository.findFirstByDocumentIdOrderByRequestedOnDesc(docId)).thenReturn(Optional.of(job));
        when(fieldRepository.findById(fieldId)).thenReturn(Optional.of(field));
        when(fieldRepository.findByJobIdOrderByFieldName(job.getId())).thenReturn(List.of(field));
        when(lineItemRepository.findByJobIdOrderByLineNumber(job.getId())).thenReturn(List.of());

        service.updateFieldValue(docId, fieldId, "USD");

        assertThat(field.getFieldValue()).isEqualTo("USD");
        verify(fieldRepository).save(field);
    }

    @Test
    void updateFieldValueUnknownDocumentThrows() {
        UUID docId = UUID.randomUUID();
        when(jobRepository.findFirstByDocumentIdOrderByRequestedOnDesc(docId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateFieldValue(docId, UUID.randomUUID(), "x"))
                .isInstanceOf(JobNotFoundException.class);
    }

    @Test
    void updateLineItemChangesTheStoredValues() {
        UUID docId = UUID.randomUUID();
        UUID lineItemId = UUID.randomUUID();
        ExtractionJob job = queuedJob(UUID.randomUUID(), "C:/x.pdf");
        ExtractedLineItem item = ExtractedLineItem.builder()
                .id(lineItemId).job(job).description("Old").totalAmount(1.0).build();
        when(jobRepository.findFirstByDocumentIdOrderByRequestedOnDesc(docId)).thenReturn(Optional.of(job));
        when(lineItemRepository.findById(lineItemId)).thenReturn(Optional.of(item));
        when(fieldRepository.findByJobIdOrderByFieldName(job.getId())).thenReturn(List.of());
        when(lineItemRepository.findByJobIdOrderByLineNumber(job.getId())).thenReturn(List.of(item));

        service.updateLineItem(docId, lineItemId,
                new UpdateLineItemRequest(1, "New", 2.0, 3.0, 20.0, 6.0, "Cat"));

        assertThat(item.getDescription()).isEqualTo("New");
        assertThat(item.getTotalAmount()).isEqualTo(6.0);
        verify(lineItemRepository).save(item);
    }
}
