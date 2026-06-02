package com.datamine.analysis.skills.tool;

import com.datamine.analysis.common.entity.Report;
import com.datamine.analysis.common.repository.ReportRepository;
import com.datamine.analysis.skills.input.SaveChartReportInput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SaveChartReportToolService {

    public static final String TOOL_NAME = "save_chart_report";
    public static final String TOOL_DESCRIPTION = "将已经生成好的 ECharts 图表配置保存到报表中心";

    private static final int MAX_REPORT_NAME_LENGTH = 60;
    private static final String DEFAULT_CHART_NAME = "图表报表";

    private final ReportRepository reportRepository;
    private final ObjectMapper objectMapper;
    private final ToolExecutionSupport toolExecutionSupport;

    public Map<String, Object> execute(Long userId,
                                       Long connectionId,
                                       String userInput,
                                       SaveChartReportInput input,
                                       ToolContext toolContext) {
        if (connectionId == null) {
            return toolExecutionSupport.failure("No active database connection is available for saving chart report");
        }
        if (input == null || input.chartConfig() == null || input.chartConfig().isEmpty()) {
            return toolExecutionSupport.failure("Chart configuration is required");
        }

        try {
            String sql = toolExecutionSupport.requirePreparedSql(input.sql(), toolContext);
            Map<String, Object> rawResult = toolExecutionSupport.requireLatestQueryResult(toolContext);
            Report savedReport = saveChartReport(userId, connectionId, userInput, input, sql);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sql", sql);
            payload.put("rawResult", rawResult);
            payload.put("chartConfig", input.chartConfig());
            payload.put("reportId", savedReport.getId());
            payload.put("reportName", savedReport.getName());
            payload.put("savedToReportCenter", true);
            payload.put("artifactType", "chart");
            log.info("Saved chart report successfully: reportId={}, reportName={}", savedReport.getId(), savedReport.getName());
            return toolExecutionSupport.success("图表已保存到报表中心：" + savedReport.getName(), payload);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("Save chart report tool rejected current input: {}", e.getMessage());
            return toolExecutionSupport.failure(e.getMessage());
        }
    }

    private Report saveChartReport(Long userId,
                                   Long connectionId,
                                   String userInput,
                                   SaveChartReportInput input,
                                   String sql) {
        try {
            Report report = new Report();
            report.setName(resolveReportName(input.name(), userInput, input.chartConfig()));
            report.setUserId(userId);
            report.setConnectionId(connectionId);
            report.setQuery(sql);
            report.setChartType(resolveChartType(input.chartConfig()));
            report.setConfig(toJson(input.chartConfig()));
            return reportRepository.save(report);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save chart report to report center: " + e.getMessage(), e);
        }
    }

    private String resolveReportName(String explicitName, String userInput, Map<String, Object> chartOption) {
        if (StringUtils.hasText(explicitName)) {
            return normalizeReportName(explicitName);
        }

        String titleText = extractTitleText(chartOption.get("title"));
        if (StringUtils.hasText(titleText)) {
            return normalizeReportName(titleText);
        }

        if (StringUtils.hasText(userInput)) {
            return normalizeReportName(userInput);
        }

        return DEFAULT_CHART_NAME;
    }

    private String extractTitleText(Object titleValue) {
        if (titleValue instanceof String text && !text.isBlank()) {
            return text;
        }

        if (titleValue instanceof Map<?, ?> map) {
            Object text = map.get("text");
            if (text instanceof String stringValue && !stringValue.isBlank()) {
                return stringValue;
            }
        }

        if (titleValue instanceof List<?> list) {
            for (Object item : list) {
                String text = extractTitleText(item);
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private String resolveChartType(Map<String, Object> chartOption) {
        Object seriesValue = chartOption.get("series");
        if (seriesValue instanceof List<?> seriesList) {
            for (Object item : seriesList) {
                if (item instanceof Map<?, ?> series) {
                    Object type = ((Map<String, Object>) series).get("type");
                    if (type instanceof String text && !text.isBlank()) {
                        return text;
                    }
                }
            }
        }
        return "auto";
    }

    private String normalizeReportName(String value) {
        String normalized = value
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s+", " ")
                .replaceAll("[\\\\/:*?\"<>|]", "")
                .trim();

        if (normalized.isBlank()) {
            return DEFAULT_CHART_NAME;
        }

        if (normalized.length() > MAX_REPORT_NAME_LENGTH) {
            return normalized.substring(0, MAX_REPORT_NAME_LENGTH).trim();
        }

        return normalized;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize chart configuration", e);
        }
    }
}
