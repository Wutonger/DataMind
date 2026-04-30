package com.datamine.analysis.skills.input;

public record SaveMarkdownReportInput(
        String sql,
        String name,
        String content
) {
}
