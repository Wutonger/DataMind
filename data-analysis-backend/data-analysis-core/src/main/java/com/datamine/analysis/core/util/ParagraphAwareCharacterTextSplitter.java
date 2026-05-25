package com.datamine.analysis.core.util;

import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 按字符切分文本，并优先在段落、标题、换行或句末附近收口，
 * 避免中文文档在句子中间被生硬截断。
 */
public class ParagraphAwareCharacterTextSplitter extends TextSplitter {

    private static final int DEFAULT_BOUNDARY_SEARCH_WINDOW = 160;
    private static final int DEFAULT_MIN_CHUNK_LENGTH = 120;

    private final int chunkSize;
    private final int chunkOverlap;
    private final int boundarySearchWindow;
    private final int minChunkLength;

    public ParagraphAwareCharacterTextSplitter(int chunkSize, int chunkOverlap) {
        this(chunkSize, chunkOverlap, DEFAULT_BOUNDARY_SEARCH_WINDOW, DEFAULT_MIN_CHUNK_LENGTH);
    }

    public ParagraphAwareCharacterTextSplitter(int chunkSize,
                                               int chunkOverlap,
                                               int boundarySearchWindow,
                                               int minChunkLength) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be greater than 0");
        }
        if (chunkOverlap < 0) {
            throw new IllegalArgumentException("chunkOverlap must be greater than or equal to 0");
        }
        if (chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("chunkOverlap must be less than chunkSize");
        }
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.boundarySearchWindow = Math.max(0, boundarySearchWindow);
        this.minChunkLength = Math.max(1, minChunkLength);
    }

    public List<String> splitToTextChunks(String text) {
        return splitText(text);
    }

    @Override
    protected List<String> splitText(String text) {
        String sourceText = text == null ? "" : text;
        if (!StringUtils.hasText(sourceText)) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        int stepSize = Math.max(1, chunkSize - chunkOverlap);
        int start = 0;

        while (start < sourceText.length()) {
            int hardEnd = Math.min(sourceText.length(), start + chunkSize);
            int naturalEnd = findNaturalBreak(sourceText, start, hardEnd);
            if (naturalEnd <= start) {
                naturalEnd = hardEnd;
            }

            String chunk = sourceText.substring(start, naturalEnd).trim();
            if (StringUtils.hasText(chunk)) {
                chunks.add(chunk);
            }

            if (naturalEnd >= sourceText.length()) {
                break;
            }

            int nextStart = Math.max(0, naturalEnd - chunkOverlap);
            if (nextStart <= start) {
                nextStart = Math.min(sourceText.length(), start + stepSize);
            }
            start = nextStart;
        }

        return chunks;
    }

    private int findNaturalBreak(String text, int start, int hardEnd) {
        if (hardEnd >= text.length()) {
            return text.length();
        }

        int minEnd = Math.min(text.length(), start + minChunkLength);
        int windowStart = Math.max(minEnd, hardEnd - boundarySearchWindow);

        int paragraphBreak = findLastParagraphBreak(text, windowStart, hardEnd);
        if (paragraphBreak > start) {
            return paragraphBreak;
        }

        int markdownHeadingBreak = findLastMarkdownHeadingBreak(text, windowStart, hardEnd);
        if (markdownHeadingBreak > start) {
            return markdownHeadingBreak;
        }

        int lineBreak = findLastLineBreak(text, windowStart, hardEnd);
        if (lineBreak > start) {
            return lineBreak;
        }

        int sentenceBreak = findLastSentenceBreak(text, windowStart, hardEnd);
        if (sentenceBreak > start) {
            return sentenceBreak;
        }

        return hardEnd;
    }

    private int findLastParagraphBreak(String text, int from, int to) {
        for (int index = to - 1; index > from; index--) {
            if (text.charAt(index) == '\n' && text.charAt(index - 1) == '\n') {
                return skipTrailingWhitespace(text, index + 1, to);
            }
        }
        return -1;
    }

    private int findLastMarkdownHeadingBreak(String text, int from, int to) {
        for (int index = to - 1; index >= from; index--) {
            if (text.charAt(index) != '#') {
                continue;
            }
            if (!isLineStart(text, index)) {
                continue;
            }
            return index;
        }
        return -1;
    }

    private int findLastLineBreak(String text, int from, int to) {
        for (int index = to - 1; index >= from; index--) {
            if (text.charAt(index) == '\n') {
                return skipTrailingWhitespace(text, index + 1, to);
            }
        }
        return -1;
    }

    private int findLastSentenceBreak(String text, int from, int to) {
        for (int index = to - 1; index >= from; index--) {
            char current = text.charAt(index);
            if (isSentenceEnding(current)) {
                return skipTrailingWhitespace(text, index + 1, to);
            }
        }
        return -1;
    }

    private boolean isLineStart(String text, int index) {
        return index == 0 || text.charAt(index - 1) == '\n';
    }

    private boolean isSentenceEnding(char current) {
        return current == '\u3002'
                || current == '\uFF01'
                || current == '\uFF1F'
                || current == ';'
                || current == '\uFF1B'
                || current == '.'
                || current == '!'
                || current == '?';
    }

    private int skipTrailingWhitespace(String text, int index, int fallback) {
        int cursor = index;
        while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) {
            cursor++;
        }
        return cursor > 0 ? cursor : fallback;
    }
}
