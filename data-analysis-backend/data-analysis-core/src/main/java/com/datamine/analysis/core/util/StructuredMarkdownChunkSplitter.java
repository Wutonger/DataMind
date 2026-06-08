package com.datamine.analysis.core.util;

import org.springframework.ai.document.Document;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StructuredMarkdownChunkSplitter {

    private static final Pattern ATX_HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.*?)\\s*#*\\s*$");
    private static final Pattern SETEXT_HEADING_PATTERN = Pattern.compile("^\\s*(=+|-+)\\s*$");
    private static final Pattern TABLE_SEPARATOR_PATTERN = Pattern.compile("^\\s*\\|?(\\s*:?-{3,}:?\\s*\\|)+\\s*:?-{3,}:?\\s*\\|?\\s*$");
    private static final Pattern LIST_ITEM_PATTERN = Pattern.compile("^\\s*(?:[-*+]\\s+|\\d+[.)]\\s+|>\\s+)");
    private static final int MIN_CONTENT_BUDGET = 160;

    private final int chunkSize;
    private final ParagraphAwareCharacterTextSplitter fallbackSplitter;

    public StructuredMarkdownChunkSplitter(int chunkSize, int chunkOverlap) {
        this.chunkSize = chunkSize;
        this.fallbackSplitter = new ParagraphAwareCharacterTextSplitter(chunkSize, chunkOverlap);
    }

    public List<Document> split(String markdown, Map<String, Object> baseMetadata) {
        String normalizedMarkdown = normalizeMarkdown(markdown);
        if (!StringUtils.hasText(normalizedMarkdown)) {
            return List.of();
        }

        List<MarkdownSection> sections = parseSections(normalizedMarkdown);
        List<Document> chunks = new ArrayList<>();
        Map<String, Object> safeMetadata = sanitizeMetadata(baseMetadata);
        for (MarkdownSection section : sections) {
            chunks.addAll(buildSectionChunks(section, safeMetadata));
        }
        return chunks;
    }

    private List<Document> buildSectionChunks(MarkdownSection section, Map<String, Object> baseMetadata) {
        List<Document> chunks = new ArrayList<>();
        String headingPrefix = buildHeadingPrefix(section.headings());
        int contentBudget = Math.max(MIN_CONTENT_BUDGET, chunkSize - headingPrefix.length());
        StringBuilder currentBody = new StringBuilder();
        String currentBlockType = null;

        for (MarkdownBlock block : section.blocks()) {
            List<String> fragments = splitBlock(block, contentBudget);
            for (String fragment : fragments) {
                if (!StringUtils.hasText(fragment)) {
                    continue;
                }

                if (!StringUtils.hasText(currentBody)) {
                    currentBody.append(fragment);
                    currentBlockType = block.type();
                    continue;
                }

                String candidate = currentBody + "\n\n" + fragment;
                if (headingPrefix.length() + candidate.length() <= chunkSize) {
                    currentBody.append("\n\n").append(fragment);
                    currentBlockType = mergeBlockType(currentBlockType, block.type());
                    continue;
                }

                chunks.add(buildChunk(section, headingPrefix, currentBody.toString(), currentBlockType, baseMetadata));
                currentBody.setLength(0);
                currentBody.append(fragment);
                currentBlockType = block.type();
            }
        }

        if (StringUtils.hasText(currentBody)) {
            chunks.add(buildChunk(section, headingPrefix, currentBody.toString(), currentBlockType, baseMetadata));
        }

        return chunks;
    }

    private Document buildChunk(MarkdownSection section,
                                String headingPrefix,
                                String body,
                                String blockType,
                                Map<String, Object> baseMetadata) {
        Map<String, Object> metadata = new LinkedHashMap<>(baseMetadata);
        metadata.put("sectionTitle", resolveSectionTitle(section.headings()));
        metadata.put("sectionPath", buildSectionPath(section.headings()));
        metadata.put("blockType", blockType);
        String text = StringUtils.hasText(headingPrefix) ? headingPrefix + body.trim() : body.trim();
        return new Document(text, sanitizeMetadata(metadata));
    }

    private List<String> splitBlock(MarkdownBlock block, int contentBudget) {
        if (!StringUtils.hasText(block.content())) {
            return List.of();
        }
        if (block.content().length() <= contentBudget) {
            return List.of(block.content().trim());
        }
        return switch (block.type()) {
            case "table" -> splitTableBlock(block.content(), contentBudget);
            case "code" -> splitCodeBlock(block.content(), contentBudget);
            default -> splitParagraphBlock(block.content());
        };
    }

    private List<String> splitParagraphBlock(String content) {
        List<String> chunks = new ArrayList<>();
        for (String piece : fallbackSplitter.splitToTextChunks(content)) {
            if (StringUtils.hasText(piece)) {
                chunks.add(piece.trim());
            }
        }
        return chunks.isEmpty() ? List.of(content.trim()) : chunks;
    }

    private List<String> splitTableBlock(String content, int contentBudget) {
        List<String> lines = Arrays.asList(content.split("\n"));
        if (lines.size() < 3) {
            return List.of(content.trim());
        }

        String header = lines.get(0).trim();
        String separator = lines.get(1).trim();
        List<String> rows = lines.subList(2, lines.size());
        String tablePrefix = header + "\n" + separator;

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder(tablePrefix);
        for (String row : rows) {
            String trimmedRow = row.trim();
            if (!StringUtils.hasText(trimmedRow)) {
                continue;
            }

            String candidate = current + "\n" + trimmedRow;
            if (candidate.length() <= contentBudget || current.toString().equals(tablePrefix)) {
                current.append("\n").append(trimmedRow);
                if (candidate.length() <= contentBudget) {
                    continue;
                }
            } else {
                chunks.add(current.toString().trim());
                current.setLength(0);
                current.append(tablePrefix).append("\n").append(trimmedRow);
                continue;
            }

            if (current.length() > contentBudget && !current.toString().equals(tablePrefix)) {
                chunks.add(current.toString().trim());
                current.setLength(0);
                current.append(tablePrefix);
            }
        }

        if (current.length() > 0 && !current.toString().equals(tablePrefix)) {
            chunks.add(current.toString().trim());
        }
        return chunks.isEmpty() ? List.of(content.trim()) : chunks;
    }

    private List<String> splitCodeBlock(String content, int contentBudget) {
        List<String> lines = Arrays.asList(content.split("\n"));
        if (lines.size() <= 2) {
            return List.of(content.trim());
        }

        String openingFence = lines.get(0);
        String closingFence = lines.get(lines.size() - 1);
        List<String> innerLines = lines.subList(1, lines.size() - 1);
        int wrapperLength = openingFence.length() + closingFence.length() + 2;
        int innerBudget = Math.max(MIN_CONTENT_BUDGET, contentBudget - wrapperLength);

        List<String> chunks = new ArrayList<>();
        StringBuilder currentInner = new StringBuilder();
        for (String innerLine : innerLines) {
            String candidate = currentInner.length() == 0 ? innerLine : currentInner + "\n" + innerLine;
            if (candidate.length() <= innerBudget || currentInner.length() == 0) {
                if (currentInner.length() > 0) {
                    currentInner.append("\n");
                }
                currentInner.append(innerLine);
                if (candidate.length() <= innerBudget) {
                    continue;
                }
            } else {
                chunks.add(wrapCodeFence(openingFence, closingFence, currentInner.toString()));
                currentInner.setLength(0);
                currentInner.append(innerLine);
                continue;
            }

            if (currentInner.length() > innerBudget) {
                chunks.add(wrapCodeFence(openingFence, closingFence, currentInner.toString()));
                currentInner.setLength(0);
            }
        }

        if (currentInner.length() > 0) {
            chunks.add(wrapCodeFence(openingFence, closingFence, currentInner.toString()));
        }
        return chunks.isEmpty() ? List.of(content.trim()) : chunks;
    }

    private String wrapCodeFence(String openingFence, String closingFence, String body) {
        return openingFence + "\n" + body.stripTrailing() + "\n" + closingFence;
    }

    private List<MarkdownSection> parseSections(String markdown) {
        List<String> lines = new ArrayList<>(Arrays.asList(markdown.split("\n", -1)));
        int index = skipFrontMatter(lines);
        List<MarkdownSection> sections = new ArrayList<>();
        List<MarkdownHeading> headingStack = new ArrayList<>();
        List<MarkdownBlock> currentBlocks = new ArrayList<>();

        while (index < lines.size()) {
            String line = lines.get(index);
            String trimmed = line.trim();

            if (!StringUtils.hasText(trimmed)) {
                index++;
                continue;
            }

            MarkdownHeading setextHeading = parseSetextHeading(lines, index);
            if (setextHeading != null) {
                flushSection(sections, headingStack, currentBlocks);
                updateHeadingStack(headingStack, setextHeading);
                index += 2;
                continue;
            }

            MarkdownHeading atxHeading = parseAtxHeading(trimmed);
            if (atxHeading != null) {
                flushSection(sections, headingStack, currentBlocks);
                updateHeadingStack(headingStack, atxHeading);
                index++;
                continue;
            }

            if (isFenceLine(trimmed)) {
                int end = consumeCodeFence(lines, index);
                currentBlocks.add(new MarkdownBlock("code", joinLines(lines, index, end)));
                index = end + 1;
                continue;
            }

            if (isTableStart(lines, index)) {
                int end = consumeTable(lines, index);
                currentBlocks.add(new MarkdownBlock("table", joinLines(lines, index, end)));
                index = end + 1;
                continue;
            }

            int end = consumeParagraphLikeBlock(lines, index);
            String content = joinLines(lines, index, end);
            currentBlocks.add(new MarkdownBlock(resolveBlockType(content), content));
            index = end + 1;
        }

        flushSection(sections, headingStack, currentBlocks);
        return sections;
    }

    private int consumeParagraphLikeBlock(List<String> lines, int start) {
        int index = start;
        while (index + 1 < lines.size()) {
            if (!StringUtils.hasText(lines.get(index + 1).trim())) {
                break;
            }
            if (parseSetextHeading(lines, index + 1) != null) {
                break;
            }
            if (parseAtxHeading(lines.get(index + 1).trim()) != null) {
                break;
            }
            if (isFenceLine(lines.get(index + 1).trim())) {
                break;
            }
            if (isTableStart(lines, index + 1)) {
                break;
            }
            index++;
        }
        return index;
    }

    private int consumeCodeFence(List<String> lines, int start) {
        String fenceLine = lines.get(start).trim();
        String fenceMarker = fenceLine.startsWith("```") ? "```" : "~~~";
        int index = start + 1;
        while (index < lines.size()) {
            String trimmed = lines.get(index).trim();
            if (trimmed.startsWith(fenceMarker)) {
                return index;
            }
            index++;
        }
        return lines.size() - 1;
    }

    private int consumeTable(List<String> lines, int start) {
        int index = start + 2;
        while (index < lines.size() && looksLikeTableRow(lines.get(index).trim())) {
            index++;
        }
        return index - 1;
    }

    private void flushSection(List<MarkdownSection> sections,
                              List<MarkdownHeading> headingStack,
                              List<MarkdownBlock> currentBlocks) {
        if (currentBlocks.isEmpty()) {
            return;
        }
        sections.add(new MarkdownSection(List.copyOf(headingStack), List.copyOf(currentBlocks)));
        currentBlocks.clear();
    }

    private void updateHeadingStack(List<MarkdownHeading> headingStack, MarkdownHeading nextHeading) {
        while (!headingStack.isEmpty() && headingStack.get(headingStack.size() - 1).level() >= nextHeading.level()) {
            headingStack.remove(headingStack.size() - 1);
        }
        headingStack.add(nextHeading);
    }

    private MarkdownHeading parseAtxHeading(String trimmedLine) {
        Matcher matcher = ATX_HEADING_PATTERN.matcher(trimmedLine);
        if (!matcher.matches()) {
            return null;
        }
        return new MarkdownHeading(matcher.group(1).length(), matcher.group(2).trim());
    }

    private MarkdownHeading parseSetextHeading(List<String> lines, int index) {
        if (index + 1 >= lines.size()) {
            return null;
        }
        String titleLine = lines.get(index).trim();
        String underline = lines.get(index + 1).trim();
        if (!StringUtils.hasText(titleLine) || !SETEXT_HEADING_PATTERN.matcher(underline).matches()) {
            return null;
        }
        int level = underline.startsWith("=") ? 1 : 2;
        return new MarkdownHeading(level, titleLine);
    }

    private boolean isFenceLine(String trimmedLine) {
        return trimmedLine.startsWith("```") || trimmedLine.startsWith("~~~");
    }

    private boolean isTableStart(List<String> lines, int index) {
        if (index + 1 >= lines.size()) {
            return false;
        }
        String current = lines.get(index).trim();
        String next = lines.get(index + 1).trim();
        return looksLikeTableRow(current) && TABLE_SEPARATOR_PATTERN.matcher(next).matches();
    }

    private boolean looksLikeTableRow(String trimmedLine) {
        return StringUtils.hasText(trimmedLine)
                && trimmedLine.contains("|")
                && !isFenceLine(trimmedLine)
                && parseAtxHeading(trimmedLine) == null;
    }

    private String resolveBlockType(String content) {
        String firstLine = content.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
        if (LIST_ITEM_PATTERN.matcher(firstLine).matches()) {
            return "list";
        }
        return "paragraph";
    }

    private String buildHeadingPrefix(List<MarkdownHeading> headings) {
        if (headings.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (MarkdownHeading heading : headings) {
            builder.append("#".repeat(Math.max(1, heading.level())))
                    .append(' ')
                    .append(heading.title())
                    .append("\n");
        }
        builder.append("\n");
        return builder.toString();
    }

    private String resolveSectionTitle(List<MarkdownHeading> headings) {
        if (headings.isEmpty()) {
            return null;
        }
        return headings.get(headings.size() - 1).title();
    }

    private String buildSectionPath(List<MarkdownHeading> headings) {
        if (headings.isEmpty()) {
            return null;
        }
        return headings.stream()
                .map(MarkdownHeading::title)
                .filter(StringUtils::hasText)
                .reduce((left, right) -> left + " > " + right)
                .orElse(null);
    }

    private String joinLines(List<String> lines, int start, int end) {
        StringBuilder builder = new StringBuilder();
        for (int index = start; index <= end && index < lines.size(); index++) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(lines.get(index));
        }
        return builder.toString().trim();
    }

    private int skipFrontMatter(List<String> lines) {
        if (lines.size() < 3 || !"---".equals(lines.get(0).trim())) {
            return 0;
        }
        for (int index = 1; index < lines.size(); index++) {
            if ("---".equals(lines.get(index).trim())) {
                return index + 1;
            }
        }
        return 0;
    }

    private String mergeBlockType(String currentType, String nextType) {
        if (!StringUtils.hasText(currentType)) {
            return nextType;
        }
        if (!StringUtils.hasText(nextType) || currentType.equals(nextType)) {
            return currentType;
        }
        return "mixed";
    }

    private String normalizeMarkdown(String markdown) {
        if (markdown == null) {
            return "";
        }
        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.startsWith("\uFEFF")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        if (metadata == null || metadata.isEmpty()) {
            return sanitized;
        }
        metadata.forEach((key, value) -> {
            if (key != null && value != null) {
                sanitized.put(key, value);
            }
        });
        return sanitized;
    }

    private record MarkdownHeading(int level, String title) {
    }

    private record MarkdownBlock(String type, String content) {
    }

    private record MarkdownSection(List<MarkdownHeading> headings, List<MarkdownBlock> blocks) {
    }
}
