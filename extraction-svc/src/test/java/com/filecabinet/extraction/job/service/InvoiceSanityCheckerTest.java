package com.filecabinet.extraction.job.service;

import com.filecabinet.extraction.ai.ExtractedFieldData;
import com.filecabinet.extraction.ai.LineItemData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceSanityCheckerTest {

    private final InvoiceSanityChecker checker = new InvoiceSanityChecker();

    private ExtractedFieldData field(String name, String value) {
        return new ExtractedFieldData(name, value, 1.0);
    }

    private LineItemData line(double total) {
        return new LineItemData(null, "item", null, null, null, total, null);
    }

    @Test
    void consistentInvoiceProducesNoWarnings() {
        List<ExtractedFieldData> fields = List.of(
                field("total_net", "100.00"),
                field("total_tax", "20.00"),
                field("total_gross", "120.00"),
                field("tax_rate_percent", "20"));
        List<LineItemData> lines = List.of(line(60.00), line(40.00));
        assertThat(checker.check(fields, lines)).isEmpty();
    }

    @Test
    void smallRoundingWithinToleranceIsAccepted() {
        List<ExtractedFieldData> fields = List.of(
                field("total_net", "100.00"),
                field("total_gross", "120.00"));
        List<LineItemData> lines = List.of(line(59.99), line(40.02));
        assertThat(checker.check(fields, lines)).isEmpty();
    }

    @Test
    void lineItemsNotMatchingTotalsAreFlagged() {
        List<ExtractedFieldData> fields = List.of(
                field("total_net", "100.00"),
                field("total_gross", "120.00"));
        List<LineItemData> lines = List.of(line(60.00), line(25.00));
        assertThat(checker.check(fields, lines)).anyMatch(w -> w.contains("Line items add up"));
    }

    @Test
    void discountAndChargesReconcileWithoutWarning() {
        List<ExtractedFieldData> fields = List.of(
                field("total_net", "6582.29"),
                field("total_gross", "6582.29"),
                field("total_discount", "1573.96"),
                field("total_charges", "286.46"));
        List<LineItemData> lines = List.of(line(7869.79));
        assertThat(checker.check(fields, lines)).isEmpty();
    }

    @Test
    void discountAndChargesThatStillDoNotReconcileAreFlagged() {
        List<ExtractedFieldData> fields = List.of(
                field("total_net", "5000.00"),
                field("total_gross", "5000.00"),
                field("total_discount", "1573.96"),
                field("total_charges", "286.46"));
        List<LineItemData> lines = List.of(line(7869.79));
        assertThat(checker.check(fields, lines)).anyMatch(w -> w.contains("after discount and charges"));
    }

    @Test
    void netPlusTaxNotEqualGrossIsFlagged() {
        List<ExtractedFieldData> fields = List.of(
                field("total_net", "100.00"),
                field("total_tax", "20.00"),
                field("total_gross", "130.00"));
        assertThat(checker.check(fields, List.of())).anyMatch(w -> w.contains("invoice total"));
    }

    @Test
    void taxNotMatchingRateIsFlagged() {
        List<ExtractedFieldData> fields = List.of(
                field("total_net", "100.00"),
                field("total_tax", "10.00"),
                field("total_gross", "110.00"),
                field("tax_rate_percent", "20"));
        assertThat(checker.check(fields, List.of())).anyMatch(w -> w.contains("does not match 20%"));
    }

    @Test
    void missingTotalsProduceNoWarnings() {
        assertThat(checker.check(List.of(), List.of())).isEmpty();
    }
}
