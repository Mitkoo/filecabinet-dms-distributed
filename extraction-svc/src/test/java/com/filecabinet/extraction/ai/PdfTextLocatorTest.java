package com.filecabinet.extraction.ai;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PdfTextLocatorTest {

    private PDDocument documentWith(String... lines) throws Exception {
        PDDocument document = new PDDocument();
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            stream.beginText();
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            stream.setLeading(16);
            stream.newLineAtOffset(60, 720);
            for (String line : lines) {
                stream.showText(line);
                stream.newLine();
            }
            stream.endText();
        }
        return document;
    }

    private FieldBox locate(PDDocument document, String value) throws Exception {
        PdfTextLocator locator = new PdfTextLocator();
        locator.getText(document);
        return locator.locate(value);
    }

    @Test
    void locatesAValuePresentInTheText() throws Exception {
        try (PDDocument document = documentWith("Total 1234.56 EUR")) {
            FieldBox box = locate(document, "1234.56");
            assertThat(box).isNotNull();
            assertThat(box.page()).isEqualTo(1);
            assertThat(box.width()).isGreaterThan(0);
            assertThat(box.height()).isGreaterThan(0);
            assertThat(box.pageWidth()).isGreaterThan(0);
        }
    }

    @Test
    void matchesDespiteCommaDecimalSeparator() throws Exception {
        try (PDDocument document = documentWith("Sum 1234,56")) {
            assertThat(locate(document, "1234.56")).isNotNull();
        }
    }

    @Test
    void matchesAcrossWhitespace() throws Exception {
        try (PDDocument document = documentWith("Supplier ACME CORP")) {
            assertThat(locate(document, "ACME CORP")).isNotNull();
        }
    }

    @Test
    void locatesIsoDatePrintedInEuropeanFormat() throws Exception {
        try (PDDocument document = documentWith("Date 26.03.2026")) {
            assertThat(locate(document, "2026-03-26")).isNotNull();
        }
    }

    @Test
    void locatesNumberWithEuropeanThousandsSeparator() throws Exception {
        try (PDDocument document = documentWith("Total 1.234,56 EUR")) {
            assertThat(locate(document, "1234.56")).isNotNull();
        }
    }

    @Test
    void doesNotMatchShortNumberInsideLongerNumber() throws Exception {
        try (PDDocument document = documentWith("Invoice 1000200030")) {
            assertThat(locate(document, "20")).isNull();
        }
    }

    @Test
    void returnsNullForValueNotInText() throws Exception {
        try (PDDocument document = documentWith("Total 100.00")) {
            assertThat(locate(document, "nothing-here")).isNull();
        }
    }

    @Test
    void returnsNullForTooShortValue() throws Exception {
        try (PDDocument document = documentWith("A 1")) {
            assertThat(locate(document, "1")).isNull();
        }
    }
}
