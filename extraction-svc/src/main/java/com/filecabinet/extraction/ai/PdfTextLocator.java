package com.filecabinet.extraction.ai;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PdfTextLocator extends PDFTextStripper {

    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4})-(\\d{2})-(\\d{2})");
    private static final Pattern NUMBER = Pattern.compile("-?\\d[\\d .,]*\\d|-?\\d");

    private record Glyph(int page, String text, float left, float top, float width, float height) {
    }

    private final List<Glyph> glyphs = new ArrayList<>();
    private final Map<Integer, float[]> pageDimensions = new LinkedHashMap<>();

    public PdfTextLocator() throws IOException {
        super();
    }

    @Override
    protected void startPage(PDPage page) throws IOException {
        PDRectangle box = page.getCropBox();
        pageDimensions.put(getCurrentPageNo(), new float[]{box.getWidth(), box.getHeight()});
        super.startPage(page);
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
        int page = getCurrentPageNo();
        for (TextPosition position : textPositions) {
            float height = position.getHeightDir();
            glyphs.add(new Glyph(page, position.getUnicode(),
                    position.getXDirAdj(), position.getYDirAdj() - height,
                    position.getWidthDirAdj(), height));
        }
        super.writeString(text, textPositions);
    }

    public FieldBox locate(String value) {
        List<String> candidates = candidates(value);
        if (candidates.isEmpty()) {
            return null;
        }
        Map<Integer, List<Glyph>> byPage = new LinkedHashMap<>();
        for (Glyph glyph : glyphs) {
            byPage.computeIfAbsent(glyph.page(), key -> new ArrayList<>()).add(glyph);
        }
        for (String candidate : candidates) {
            for (Map.Entry<Integer, List<Glyph>> entry : byPage.entrySet()) {
                StringBuilder normalized = new StringBuilder();
                List<Glyph> indexToGlyph = new ArrayList<>();
                for (Glyph glyph : entry.getValue()) {
                    String normalizedGlyph = normalize(glyph.text());
                    for (int i = 0; i < normalizedGlyph.length(); i++) {
                        normalized.append(normalizedGlyph.charAt(i));
                        indexToGlyph.add(glyph);
                    }
                }
                boolean guardBoundary = isShortNumber(candidate);
                int from = 0;
                int index;
                while ((index = normalized.indexOf(candidate, from)) >= 0) {
                    if (!guardBoundary || boundaryOk(normalized, index, candidate.length())) {
                        return union(indexToGlyph.subList(index, index + candidate.length()), entry.getKey());
                    }
                    from = index + 1;
                }
            }
        }
        return null;
    }

    private List<String> candidates(String value) {
        Set<String> raw = new LinkedHashSet<>();
        if (value != null && !value.isBlank()) {
            raw.add(value);
            raw.addAll(dateVariants(value));
            raw.addAll(numberVariants(value));
        }
        List<String> normalized = new ArrayList<>();
        for (String candidate : raw) {
            String norm = normalize(candidate);
            if (norm.length() >= 2 && !normalized.contains(norm)) {
                normalized.add(norm);
            }
        }
        return normalized;
    }

    private List<String> dateVariants(String value) {
        Matcher matcher = ISO_DATE.matcher(value.trim());
        if (!matcher.matches()) {
            return List.of();
        }
        String year = matcher.group(1);
        String month = matcher.group(2);
        String day = matcher.group(3);
        String shortMonth = String.valueOf(Integer.parseInt(month));
        String shortDay = String.valueOf(Integer.parseInt(day));
        List<String> variants = new ArrayList<>();
        for (String sep : new String[]{".", "/", "-", " "}) {
            variants.add(day + sep + month + sep + year);
            variants.add(shortDay + sep + shortMonth + sep + year);
            variants.add(month + sep + day + sep + year);
            variants.add(year + sep + month + sep + day);
        }
        return variants;
    }

    private List<String> numberVariants(String value) {
        String trimmed = value.trim();
        if (!NUMBER.matcher(trimmed).matches()) {
            return List.of();
        }
        BigDecimal number;
        try {
            number = new BigDecimal(trimmed.replace(" ", "").replace(",", "."));
        } catch (NumberFormatException e) {
            return List.of();
        }
        number = number.stripTrailingZeros();
        int scale = Math.max(number.scale(), 0);
        String plain = number.toPlainString();
        String dot = scale == 0 ? plain : new BigDecimal(number.toPlainString()).toPlainString();
        String withComma = dot.replace('.', ',');
        List<String> variants = new ArrayList<>();
        variants.add(dot);
        variants.add(withComma);
        variants.add(grouped(number, scale, '.', ','));
        variants.add(grouped(number, scale, ',', '.'));
        return variants;
    }

    private String grouped(BigDecimal number, int scale, char thousands, char decimal) {
        String plain = number.abs().toPlainString();
        String intPart = plain;
        String fraction = "";
        int dot = plain.indexOf('.');
        if (dot >= 0) {
            intPart = plain.substring(0, dot);
            fraction = plain.substring(dot + 1);
        }
        StringBuilder grouped = new StringBuilder();
        int count = 0;
        for (int i = intPart.length() - 1; i >= 0; i--) {
            grouped.append(intPart.charAt(i));
            if (++count % 3 == 0 && i > 0) {
                grouped.append(thousands);
            }
        }
        grouped.reverse();
        String result = grouped.toString();
        if (scale > 0 && !fraction.isEmpty()) {
            result = result + decimal + fraction;
        }
        return number.signum() < 0 ? "-" + result : result;
    }

    private boolean isShortNumber(String candidate) {
        int digits = 0;
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            if (Character.isDigit(c)) {
                digits++;
            } else if (".,/-".indexOf(c) < 0) {
                return false;
            }
        }
        return digits > 0 && digits <= 2;
    }

    private boolean boundaryOk(CharSequence text, int start, int length) {
        if (start > 0 && Character.isDigit(text.charAt(start - 1))) {
            return false;
        }
        int end = start + length;
        return end >= text.length() || !Character.isDigit(text.charAt(end));
    }

    private FieldBox union(List<Glyph> span, int page) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = 0;
        float maxY = 0;
        for (Glyph glyph : span) {
            minX = Math.min(minX, glyph.left());
            minY = Math.min(minY, glyph.top());
            maxX = Math.max(maxX, glyph.left() + glyph.width());
            maxY = Math.max(maxY, glyph.top() + glyph.height());
        }
        float[] dimensions = pageDimensions.getOrDefault(page, new float[]{maxX, maxY});
        return new FieldBox(page, minX, minY, maxX - minX, maxY - minY, dimensions[0], dimensions[1]);
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (char character : text.toCharArray()) {
            if (!Character.isWhitespace(character)) {
                builder.append(Character.toLowerCase(character));
            }
        }
        return builder.toString();
    }
}
