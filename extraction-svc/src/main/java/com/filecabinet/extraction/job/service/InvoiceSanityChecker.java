package com.filecabinet.extraction.job.service;

import com.filecabinet.extraction.ai.ExtractedFieldData;
import com.filecabinet.extraction.ai.LineItemData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class InvoiceSanityChecker {

    private static final double ABS_TOLERANCE = 0.05;
    private static final double PER_LINE_TOLERANCE = 0.02;

    public List<String> check(List<ExtractedFieldData> fields, List<LineItemData> lineItems) {
        List<String> warnings = new ArrayList<>();

        Map<String, String> values = new HashMap<>();
        if (fields != null) {
            for (ExtractedFieldData field : fields) {
                if (field.name() != null) {
                    values.put(field.name(), field.value());
                }
            }
        }
        Double net = parse(values.get("total_net"));
        Double tax = parse(values.get("total_tax"));
        Double gross = parse(values.get("total_gross"));
        Double taxRate = parse(values.get("tax_rate_percent"));

        if (lineItems != null && !lineItems.isEmpty() && (net != null || gross != null)) {
            double sum = lineItems.stream()
                    .map(LineItemData::totalAmount)
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .sum();
            double tolerance = ABS_TOLERANCE + PER_LINE_TOLERANCE * lineItems.size();
            boolean matchesNet = net != null && Math.abs(sum - net) <= tolerance;
            boolean matchesGross = gross != null && Math.abs(sum - gross) <= tolerance;
            if (!matchesNet && !matchesGross) {
                warnings.add(String.format(
                        "Line items add up to %s, which does not match the invoice net (%s) or gross (%s).",
                        money(sum), money(net), money(gross)));
            }
        }

        if (net != null && tax != null && gross != null && Math.abs(net + tax - gross) > ABS_TOLERANCE) {
            warnings.add(String.format(
                    "Net (%s) plus tax (%s) is %s, but the invoice total is %s.",
                    money(net), money(tax), money(net + tax), money(gross)));
        }

        if (net != null && tax != null && taxRate != null && taxRate > 0) {
            double expected = net * taxRate / 100.0;
            if (Math.abs(expected - tax) > Math.max(ABS_TOLERANCE, net * 0.005)) {
                warnings.add(String.format(
                        "Tax (%s) does not match %s%% of the net (expected about %s).",
                        money(tax), trimRate(taxRate), money(expected)));
            }
        }

        return warnings;
    }

    private Double parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = value.replaceAll("[^0-9.\\-]", "");
        if (cleaned.isEmpty() || cleaned.equals("-") || cleaned.equals(".")) {
            return null;
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String money(Double value) {
        return value == null ? "n/a" : String.format("%.2f", value);
    }

    private String trimRate(double rate) {
        if (rate == Math.rint(rate)) {
            return String.valueOf((long) rate);
        }
        return String.valueOf(rate);
    }
}
