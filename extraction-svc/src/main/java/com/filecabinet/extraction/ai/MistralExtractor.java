package com.filecabinet.extraction.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filecabinet.extraction.shared.exception.ExtractionExceptions.ExtractionFailedException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class MistralExtractor implements ExtractorPort {

    private static final String USER_INSTRUCTION =
            "Extract all invoice information from this document and return the JSON.";

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final String systemPrompt;

    public MistralExtractor(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.systemPrompt = loadSystemPrompt();
    }

    @Override
    public String providerName() {
        return "mistral";
    }

    @Override
    public ExtractionResult extract(byte[] fileBytes, String filename) {
        ParsedPdf pdf = readPdf(fileBytes, filename);

        String userMessage = "PDF text content:\n\n" + pdf.text() + "\n\n" + USER_INSTRUCTION;

        String raw = chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();

        JsonNode root = parseJson(raw);
        return new ExtractionResult(flatten(root, pdf.locator()), parseLineItems(root, pdf.locator()));
    }

    private List<LineItemData> parseLineItems(JsonNode root, PdfTextLocator locator) {
        List<LineItemData> items = new ArrayList<>();
        JsonNode lineItems = root.get("line_items");
        if (lineItems != null && lineItems.isArray()) {
            for (JsonNode item : lineItems) {
                String category = textOrNull(item, "category");
                if (category == null) {
                    category = textOrNull(item, "category_suggestion");
                }
                String description = textOrNull(item, "description");
                Double totalAmount = doubleOrNull(item, "total_amount");
                FieldBox box = locateLineItem(locator, description, totalAmount);
                items.add(new LineItemData(
                        intOrNull(item, "line_number"),
                        description,
                        doubleOrNull(item, "quantity"),
                        doubleOrNull(item, "unit_price"),
                        doubleOrNull(item, "vat_rate_percent"),
                        totalAmount,
                        category,
                        box));
            }
        }
        return items;
    }

    private FieldBox locateLineItem(PdfTextLocator locator, String description, Double totalAmount) {
        if (description != null && description.length() >= 4) {
            FieldBox box = locator.locate(description);
            if (box == null && description.length() > 24) {
                box = locator.locate(description.substring(0, 24));
            }
            if (box != null) {
                return box;
            }
        }
        return totalAmount != null ? locator.locate(formatAmount(totalAmount)) : null;
    }

    private String formatAmount(double amount) {
        return java.math.BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString();
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? value.asText() : null;
    }

    private Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asInt() : null;
    }

    private Double doubleOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isNumber() ? value.asDouble() : null;
    }

    private record ParsedPdf(String text, PdfTextLocator locator) {
    }

    private ParsedPdf readPdf(byte[] fileBytes, String filename) {
        if (filename != null && !filename.toLowerCase().endsWith(".pdf")) {
            throw new ExtractionFailedException("Only PDF documents are supported, got: " + filename);
        }
        try (PDDocument document = Loader.loadPDF(fileBytes)) {
            PdfTextLocator locator = new PdfTextLocator();
            String text = locator.getText(document).strip();
            if (text.isEmpty()) {
                throw new ExtractionFailedException("PDF has no readable text layer: " + filename);
            }
            return new ParsedPdf(text, locator);
        } catch (IOException e) {
            throw new ExtractionFailedException("Could not read PDF: " + filename, e);
        }
    }

    private JsonNode parseJson(String raw) {
        String cleaned = raw.strip();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        try {
            return objectMapper.readTree(cleaned);
        } catch (IOException e) {
            throw new ExtractionFailedException("Model did not return valid JSON", e);
        }
    }

    private List<ExtractedFieldData> flatten(JsonNode root, PdfTextLocator locator) {
        List<ExtractedFieldData> fields = new ArrayList<>();

        addScalar(fields, root, "invoice_number", locator);
        addScalar(fields, root, "invoice_date", locator);
        addScalar(fields, root, "due_date", locator);
        addScalar(fields, root, "currency", locator);
        addScalar(fields, root, "subtotal", locator);
        addScalar(fields, root, "total_net", locator);
        addScalar(fields, root, "total_tax", locator);
        addScalar(fields, root, "total_gross", locator);
        addScalar(fields, root, "total_discount", locator);
        addScalar(fields, root, "total_charges", locator);
        addScalar(fields, root, "tax_rate_percent", locator);
        addScalar(fields, root, "payment_terms", locator);

        JsonNode supplier = root.get("supplier");
        if (supplier != null && supplier.isObject()) {
            addNamed(fields, "supplier_legal_name", supplier.get("legal_name"), locator);
            addNamed(fields, "supplier_vat_number", supplier.get("vat_number"), locator);
            addNamed(fields, "supplier_country", supplier.get("country"), locator);
            addNamed(fields, "supplier_city", supplier.get("city"), locator);
        }

        JsonNode lineItems = root.get("line_items");
        if (lineItems != null && lineItems.isArray()) {
            fields.add(new ExtractedFieldData("line_items_count", String.valueOf(lineItems.size()), 1.0));
        }

        JsonNode manualReview = root.get("requires_manual_review");
        if (manualReview != null && !manualReview.isNull()) {
            fields.add(new ExtractedFieldData("requires_manual_review", manualReview.asText(), 1.0));
        }

        return fields;
    }

    private void addScalar(List<ExtractedFieldData> fields, JsonNode root, String name, PdfTextLocator locator) {
        addNamed(fields, name, root.get(name), locator);
    }

    private void addNamed(List<ExtractedFieldData> fields, String name, JsonNode value, PdfTextLocator locator) {
        if (value != null && !value.isNull()) {
            String text = value.asText();
            fields.add(new ExtractedFieldData(name, text, 1.0, locator.locate(text)));
        }
    }

    private String loadSystemPrompt() {
        try {
            return StreamUtils.copyToString(
                    new ClassPathResource("system-prompt.txt").getInputStream(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load system prompt", e);
        }
    }
}
