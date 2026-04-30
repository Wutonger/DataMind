package com.datamine.analysis.agent.tool;

import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

public final class ToolNameNormalizer {

    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("([a-z0-9])([A-Z])");

    private ToolNameNormalizer() {
    }

    public static String canonicalize(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return "";
        }

        String normalized = toolName.trim()
                .replace('-', '_')
                .replace(' ', '_');
        normalized = CAMEL_CASE_PATTERN.matcher(normalized).replaceAll("$1_$2");
        return normalized.toLowerCase(Locale.ROOT);
    }
}
