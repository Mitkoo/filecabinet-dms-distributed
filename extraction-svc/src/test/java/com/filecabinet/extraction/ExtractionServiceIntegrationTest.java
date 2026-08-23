package com.filecabinet.extraction;

import com.filecabinet.extraction.ai.ExtractedFieldData;
import com.filecabinet.extraction.ai.ExtractionResult;
import com.filecabinet.extraction.ai.ExtractorPort;
import com.filecabinet.extraction.ai.LineItemData;
import com.filecabinet.extraction.job.service.ExtractionJobService;
import com.filecabinet.extraction.scheduling.ExtractionScheduler;
import com.filecabinet.extraction.web.dto.CreateExtractionRequest;
import com.filecabinet.extraction.web.dto.ExtractionJobResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ExtractionServiceIntegrationTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:extraction;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.ai.mistralai.api-key", () -> "test-key");
    }

    @TestConfiguration
    static class StubExtractorConfig {
        @Bean
        @Primary
        ExtractorPort stubExtractor() {
            return new ExtractorPort() {
                @Override
                public ExtractionResult extract(byte[] fileBytes, String filename) {
                    List<ExtractedFieldData> fields = List.of(
                            new ExtractedFieldData("total_net", "1000.00", 1.0),
                            new ExtractedFieldData("supplier_legal_name", "ACME CORP", 1.0));
                    List<LineItemData> lineItems = List.of(
                            new LineItemData(1, "Sensor", 3.0, 15.11, 20.0, 45.33, "Hardware"));
                    return new ExtractionResult(fields, lineItems);
                }

                @Override
                public String providerName() {
                    return "stub";
                }
            };
        }
    }

    @MockitoBean
    private ExtractionScheduler extractionScheduler;

    @Autowired
    private ExtractionJobService jobService;

    @Test
    void fullJobLifecyclePersistsExtractedFields(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("invoice.pdf");
        Files.write(file, new byte[]{1, 2, 3});
        UUID documentId = UUID.randomUUID();

        ExtractionJobResponse queued = jobService.queue(new CreateExtractionRequest(documentId, file.toString()));
        assertThat(queued.status()).isEqualTo("QUEUED");

        jobService.processJob(queued.id());

        ExtractionJobResponse result = jobService.getByDocument(documentId);
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.fields()).extracting("fieldName")
                .contains("total_net", "supplier_legal_name");
        assertThat(result.fields()).extracting("fieldValue").contains("ACME CORP");
        assertThat(result.lineItems()).hasSize(1);
        assertThat(result.lineItems().get(0).description()).isEqualTo("Sensor");
    }
}
