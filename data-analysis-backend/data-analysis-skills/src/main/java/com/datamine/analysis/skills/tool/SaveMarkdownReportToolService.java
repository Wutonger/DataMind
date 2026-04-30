package com.datamine.analysis.skills.tool;

import com.datamine.analysis.common.entity.Report;
import com.datamine.analysis.common.repository.ReportRepository;
import com.datamine.analysis.skills.input.SaveMarkdownReportInput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SaveMarkdownReportToolService {

    public static final String TOOL_NAME = "save_markdown_report";
    public static final String TOOL_DESCRIPTION = "将已经生成好的 Markdown 报告保存到报表中心";

    private static final int MAX_REPORT_NAME_LENGTH = 60;
    private static final String DEFAULT_REPORT_NAME = "分析报告";

    private final ReportRepository reportRepository;
    private final ObjectMapper objectMapper;
    private final ToolExecutionSupport toolExecutionSupport;

    public Map<String, Object> execute(Long connectionId,
                                       String userInput,
                                       SaveMarkdownReportInput input,
                                       ToolContext toolContext) {
        if (connectionId == null) {
            return toolExecutionSupport.failure("No active database connection is available for saving markdown report");
        }
        if (input == null || !StringUtils.hasText(input.content())) {
            return toolExecutionSupport.failure("Markdown report content is required");
        }

        try {
            String sql = toolExecutionSupport.requirePreparedSql(input.sql(), toolContext);
            Map<String, Object> rawResult = toolExecutionSupport.requireLatestQueryResult(toolContext);
            Report savedReport = saveReport(connectionId, userInput, input, sql);

            Map<String, Object> reportConfig = Map.of(
                    "type", "markdown",
                    "content", input.content()
            );

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sql", sql);
            payload.put("rawResult", rawResult);
            payload.put("report", input.content());
            payload.put("reportConfig", reportConfig);
            payload.put("reportId", savedReport.getId());
            payload.put("reportName", savedReport.getName());
            payload.put("savedToReportCenter", true);
            payload.put("artifactType", "report");
            log.info("Saved markdown report successfully: reportId={}, reportName={}", savedReport.getId(), savedReport.getName());
            return toolExecutionSupport.success("文档报告已保存到报表中心：" + savedReport.getName(), payload);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Save markdown report tool rejected current input: {}", e.getMessage());
            return toolExecutionSupport.failure(e.getMessage());
        }
    }

    private Report saveReport(Long connectionId,
                              String userInput,
                              SaveMarkdownReportInput input,
                              String sql) {
        try {
            Report report = new Report();
            report.setName(resolveReportName(input.name(), userInput, input.content()));
            report.setConnectionId(connectionId);
            report.setQuery(sql);
            report.setChartType("report");
            report.setConfig(buildStoredConfig(input.content()));
            return reportRepository.save(report);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save report to report center: " + e.getMessage(), e);
        }
    }

    private String buildStoredConfig(String reportContent) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "type", "markdown",
                    "content", reportContent
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize report content", e);
        }
    }

    private String resolveReportName(String explicitName, String userInput, String reportContent) {
        if (StringUtils.hasText(explicitName)) {
            return normalizeReportName(explicitName);
        }

        String titleFromReport = extractHeading(reportContent);
        if (titleFromReport != null) {
            return titleFromReport;
        }

        if (StringUtils.hasText(userInput)) {
            return normalizeReportName(userInput);
        }

        return DEFAULT_REPORT_NAME;
    }

    private String extractHeading(String reportContent) {
        if (reportContent == null || reportContent.isBlank()) {
            return null;
        }

        for (String line : reportContent.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("#")) {
                continue;
            }

            String heading = trimmed.replaceFirst("^#+\\s*", "");
            if (!heading.isBlank()) {
                return normalizeReportName(heading);
            }
        }

        return null;
    }

    private String normalizeReportName(String value) {
        String normalized = value
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s+", " ")
                .replaceAll("[\\\\/:*?\"<>|]", "")
                .trim();

        if (normalized.isBlank()) {
            return DEFAULT_REPORT_NAME;
        }

        if (normalized.length() > MAX_REPORT_NAME_LENGTH) {
            return normalized.substring(0, MAX_REPORT_NAME_LENGTH).trim();
        }

        return normalized;
    }
}
