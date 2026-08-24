package com.filecabinet.extraction.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filecabinet.extraction.shared.exception.ExtractionExceptions.ExtractionFailedException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MistralExtractorTest {

    private ChatClient.CallResponseSpec callSpec;
    private MistralExtractor extractor;

    @BeforeEach
    void setUp() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callSpec = mock(ChatClient.CallResponseSpec.class);
        when(builder.build()).thenReturn(client);
        when(client.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        extractor = new MistralExtractor(builder, new ObjectMapper());
    }

    private byte[] samplePdf() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText("Invoice INV-1001 total 1234.56");
                stream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void extractFlattensScalarSupplierAndLineItems() throws Exception {
        String json = """
                {
                  "supplier": {"legal_name": "ACME CORP", "vat_number": "BG123456789", "country": "Testland", "city": "Metropolis"},
                  "invoice_number": "INV-1001",
                  "invoice_date": "2026-01-15",
                  "currency": "EUR",
                  "total_net": 1000.00,
                  "total_tax": 234.56,
                  "total_gross": 1234.56,
                  "line_items": [{"description": "a", "quantity": 2, "total_amount": 10}, {"description": "b"}],
                  "requires_manual_review": false
                }
                """;
        when(callSpec.content()).thenReturn(json);

        ExtractionResult result = extractor.extract(samplePdf(), "invoice.pdf");
        List<ExtractedFieldData> fields = result.fields();

        assertThat(result.lineItems()).hasSize(2);
        assertThat(result.lineItems().get(0).description()).isEqualTo("a");
        assertThat(result.lineItems().get(0).totalAmount()).isEqualTo(10.0);
        assertThat(fields).extracting(ExtractedFieldData::name)
                .contains("supplier_legal_name", "invoice_number", "total_gross", "line_items_count");
        assertThat(fields).anySatisfy(f -> {
            assertThat(f.name()).isEqualTo("supplier_legal_name");
            assertThat(f.value()).isEqualTo("ACME CORP");
        });
        assertThat(fields).anySatisfy(f -> {
            assertThat(f.name()).isEqualTo("line_items_count");
            assertThat(f.value()).isEqualTo("2");
        });
    }

    @Test
    void extractStripsMarkdownCodeFences() throws Exception {
        when(callSpec.content()).thenReturn("```json\n{\"currency\": \"USD\"}\n```");
        List<ExtractedFieldData> fields = extractor.extract(samplePdf(), "invoice.pdf").fields();
        assertThat(fields).anySatisfy(f -> {
            assertThat(f.name()).isEqualTo("currency");
            assertThat(f.value()).isEqualTo("USD");
        });
    }

    @Test
    void extractRejectsNonPdf() {
        assertThatThrownBy(() -> extractor.extract(new byte[]{1, 2}, "image.png"))
                .isInstanceOf(ExtractionFailedException.class);
    }

    @Test
    void extractFailsOnInvalidJson() throws Exception {
        when(callSpec.content()).thenReturn("not json at all");
        assertThatThrownBy(() -> extractor.extract(samplePdf(), "invoice.pdf"))
                .isInstanceOf(ExtractionFailedException.class);
    }

    @Test
    void providerNameIsMistral() {
        assertThat(extractor.providerName()).isEqualTo("mistral");
    }
}
