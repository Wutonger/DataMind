package com.datamine.analysis.core.export;

import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.TextAlignment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Component
public class PdfExporter {

    public byte[] export(List<Map<String, Object>> data, String title) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(out);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        PdfFont font = PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H");

        addTitle(document, font, title);

        if (data == null || data.isEmpty()) {
            document.add(new Paragraph(sanitizeText(font, "No data available"))
                    .setFont(font)
                    .setFontSize(11));
            document.close();
            return out.toByteArray();
        }

        String[] headers = data.get(0).keySet().toArray(new String[0]);
        float[] widths = new float[Math.max(headers.length, 1)];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = 1;
        }

        Table table = new Table(widths).useAllAvailableWidth();

        for (String header : headers) {
            table.addHeaderCell(new Cell().add(
                    new Paragraph(sanitizeText(font, header)).setFont(font).setFontSize(9)));
        }

        for (Map<String, Object> record : data) {
            for (String header : headers) {
                Object value = record.get(header);
                String text = value != null ? value.toString() : "";
                table.addCell(new Cell().add(
                        new Paragraph(sanitizeText(font, text)).setFont(font).setFontSize(8)));
            }
        }

        document.add(table);
        document.close();

        return out.toByteArray();
    }

    public byte[] exportMarkdown(String markdown, String title) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(out);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        PdfFont font = PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H");
        addTitle(document, font, title);

        String content = markdown == null ? "" : markdown.replace("\r\n", "\n");
        if (content.isBlank()) {
            document.add(new Paragraph(sanitizeText(font, "No report content"))
                    .setFont(font)
                    .setFontSize(11));
            document.close();
            return out.toByteArray();
        }

        String[] lines = content.split("\n");
        boolean inCodeBlock = false;
        List<String> codeLines = new ArrayList<>();
        List<String> tableLines = new ArrayList<>();

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine;
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                if (!tableLines.isEmpty()) {
                    addMarkdownTable(document, font, tableLines);
                    tableLines.clear();
                }

                if (inCodeBlock) {
                    addCodeBlock(document, font, codeLines);
                    codeLines.clear();
                    inCodeBlock = false;
                } else {
                    inCodeBlock = true;
                }
                continue;
            }

            if (inCodeBlock) {
                codeLines.add(line);
                continue;
            }

            if (isTableLine(trimmed)) {
                tableLines.add(trimmed);
                continue;
            }

            if (!tableLines.isEmpty()) {
                addMarkdownTable(document, font, tableLines);
                tableLines.clear();
            }

            if (trimmed.isBlank()) {
                document.add(new Paragraph(" ").setFont(font).setMarginTop(0).setMarginBottom(4));
                continue;
            }

            if (trimmed.startsWith("#")) {
                addHeading(document, font, trimmed);
                continue;
            }

            if (isListItem(trimmed)) {
                document.add(new Paragraph(sanitizeText(font, "- " + stripInlineMarkdown(stripListMarker(trimmed))))
                        .setFont(font)
                        .setFontSize(11)
                        .setMarginTop(2)
                        .setMarginBottom(2));
                continue;
            }

            document.add(new Paragraph(sanitizeText(font, stripInlineMarkdown(trimmed)))
                    .setFont(font)
                    .setFontSize(11)
                    .setMarginTop(3)
                    .setMarginBottom(3)
                    .setMultipliedLeading(1.5f));
        }

        if (inCodeBlock && !codeLines.isEmpty()) {
            addCodeBlock(document, font, codeLines);
        }

        if (!tableLines.isEmpty()) {
            addMarkdownTable(document, font, tableLines);
        }

        document.close();
        return out.toByteArray();
    }

    private void addTitle(Document document, PdfFont font, String title) {
        if (title == null || title.isEmpty()) {
            return;
        }

        Paragraph titlePara = new Paragraph(sanitizeText(font, title))
                .setFont(font)
                .setFontSize(16)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(12);
        document.add(titlePara);
    }

    private void addHeading(Document document, PdfFont font, String line) {
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#') {
            level++;
        }

        String text = stripInlineMarkdown(line.substring(level).trim());
        float fontSize = switch (Math.min(level, 6)) {
            case 1 -> 16f;
            case 2 -> 14f;
            case 3 -> 13f;
            default -> 12f;
        };

        document.add(new Paragraph(sanitizeText(font, text))
                .setFont(font)
                .setFontSize(fontSize)
                .setBold()
                .setMarginTop(level == 1 ? 8 : 6)
                .setMarginBottom(4));
    }

    private void addCodeBlock(Document document, PdfFont font, List<String> codeLines) {
        if (codeLines.isEmpty()) {
            return;
        }

        String content = String.join("\n", codeLines);
        document.add(new Paragraph(sanitizeText(font, content))
                .setFont(font)
                .setFontSize(10)
                .setBackgroundColor(com.itextpdf.kernel.colors.ColorConstants.LIGHT_GRAY)
                .setPadding(8)
                .setMarginTop(4)
                .setMarginBottom(6));
    }

    private void addMarkdownTable(Document document, PdfFont font, List<String> tableLines) {
        if (tableLines.isEmpty()) {
            return;
        }

        List<String[]> rows = new ArrayList<>();
        for (String line : tableLines) {
            if (isTableSeparator(line)) {
                continue;
            }
            String[] cells = splitTableRow(line);
            if (cells.length > 0) {
                rows.add(cells);
            }
        }

        if (rows.isEmpty()) {
            return;
        }

        int columnCount = rows.stream().mapToInt(row -> row.length).max().orElse(1);
        Table table = new Table(UnitValue.createPercentArray(columnCount)).useAllAvailableWidth();

        String[] headers = rows.get(0);
        for (int i = 0; i < columnCount; i++) {
            table.addHeaderCell(new Cell().add(new Paragraph(sanitizeText(font, i < headers.length ? stripInlineMarkdown(headers[i]) : ""))
                    .setFont(font)
                    .setFontSize(9)));
        }

        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            String[] row = rows.get(rowIndex);
            for (int i = 0; i < columnCount; i++) {
                table.addCell(new Cell().add(new Paragraph(sanitizeText(font, i < row.length ? stripInlineMarkdown(row[i]) : ""))
                        .setFont(font)
                        .setFontSize(9)));
            }
        }

        document.add(table.setMarginTop(6).setMarginBottom(8));
    }

    private boolean isTableLine(String line) {
        return line.startsWith("|") && line.endsWith("|") && line.length() > 1;
    }

    private boolean isTableSeparator(String line) {
        return Pattern.matches("\\|?\\s*[:\\-\\s|]+\\|?", line);
    }

    private String[] splitTableRow(String line) {
        String normalized = line;
        if (normalized.startsWith("|")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("|")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.split("\\s*\\|\\s*");
    }

    private boolean isListItem(String line) {
        return line.startsWith("- ")
                || line.startsWith("* ")
                || Pattern.matches("\\d+\\.\\s+.*", line);
    }

    private String stripListMarker(String line) {
        if (line.startsWith("- ") || line.startsWith("* ")) {
            return line.substring(2).trim();
        }
        return line.replaceFirst("^\\d+\\.\\s+", "").trim();
    }

    private String stripInlineMarkdown(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        return text
                .replaceAll("!\\[[^\\]]*]\\([^)]*\\)", "")
                .replaceAll("\\[([^\\]]+)]\\([^)]*\\)", "$1")
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .replace("~~", "")
                .replace("&nbsp;", " ")
                .trim();
    }

    private String sanitizeText(PdfFont font, String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        text.codePoints().forEach(codePoint -> {
            if (codePoint == '\r' || codePoint == '\n') {
                builder.appendCodePoint(codePoint);
                return;
            }

            if (codePoint == '\t') {
                builder.append("    ");
                return;
            }

            if (Character.isISOControl(codePoint)) {
                return;
            }

            if (font.containsGlyph(codePoint)) {
                builder.appendCodePoint(codePoint);
                return;
            }

            if (codePoint < 128) {
                builder.append('?');
            } else {
                builder.append(' ');
            }
        });

        return builder.toString();
    }
}
