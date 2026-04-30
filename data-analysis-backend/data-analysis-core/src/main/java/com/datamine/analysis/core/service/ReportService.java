package com.datamine.analysis.core.service;

import com.datamine.analysis.agent.model.ReportExecutionResult;
import com.datamine.analysis.agent.orchestrator.AssistantAgentOrchestrator;
import com.datamine.analysis.common.entity.Report;
import com.datamine.analysis.common.repository.ReportRepository;
import com.datamine.analysis.core.chat.ChatClientFactory;
import com.datamine.analysis.core.export.ExcelExporter;
import com.datamine.analysis.core.export.PdfExporter;
import com.datamine.analysis.mcp.client.McpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final ChatClientFactory chatClientFactory;
    private final McpClient mcpClient;
    private final ObjectMapper objectMapper;
    private final ExcelExporter excelExporter;
    private final PdfExporter pdfExporter;
    private final AssistantAgentOrchestrator assistantAgentOrchestrator;

    public List<Report> listReports(Long connectionId) {
        return reportRepository.findByConnectionId(connectionId);
    }

    public Report getReport(Long id) {
        return reportRepository.findById(id).orElse(null);
    }

    public Report updateReport(Long id, Report updated) {
        Report report = reportRepository.findById(id).orElseThrow();
        report.setName(updated.getName());
        report.setConfig(updated.getConfig());
        report.setChartType(updated.getChartType());
        report.setQuery(updated.getQuery());
        return reportRepository.save(report);
    }

    public void deleteReport(Long id) {
        reportRepository.deleteById(id);
    }

    /**
     * 报表中心的 AI 生成功能统一走 Agent 编排层，由 Agent 自主选择图表或文档报告技能。
     */
    public Map<String, Object> generateReportArtifact(Long connectionId, String userRequirement) {
        try {
            ReportExecutionResult result = assistantAgentOrchestrator.generateReport(
                    connectionId,
                    userRequirement,
                    chatClientFactory.getChatClient()
            );
            return Map.of(
                    "artifactType", result.artifactType(),
                    "sql", result.sql(),
                    "artifactConfig", result.artifactConfig(),
                    "rawResult", result.rawResult(),
                    "workflowRunId", result.workflowRunId(),
                    "reportId", result.reportId(),
                    "reportName", result.reportName() != null ? result.reportName() : "",
                    "savedToReportCenter", result.savedToReportCenter()
            );
        } catch (Exception e) {
            log.error("AI report artifact generation failed", e);
            return Map.of(
                    "error", true,
                    "message", resolveErrorMessage(e)
            );
        }
    }

    public byte[] exportExcel(Long connectionId, String sql, String sheetName) throws IOException {
        Map<String, Object> result = mcpClient.dbExecute(connectionId, sql);
        List<Map<String, Object>> rows = extractRows(result);
        return excelExporter.export(rows, sheetName);
    }

    public byte[] exportPdf(Long connectionId, String sql, String title) throws IOException {
        Map<String, Object> result = mcpClient.dbExecute(connectionId, sql);
        List<Map<String, Object>> rows = extractRows(result);
        return pdfExporter.export(rows, title);
    }

    @SuppressWarnings("unchecked")
    public byte[] exportPdf(Long reportId, Long connectionId, String sql, String title) throws IOException {
        if (reportId != null) {
            Report report = reportRepository.findById(reportId).orElse(null);
            if (report != null) {
                Map<String, Object> config = parseConfig(report.getConfig());
                if (isMarkdownReport(report, config)) {
                    String content = config.get("content") instanceof String text ? text : "";
                    return pdfExporter.exportMarkdown(content, resolveTitle(title, report.getName()));
                }

                if (!StringUtils.hasText(sql)) {
                    sql = report.getQuery();
                }
                if (!StringUtils.hasText(title)) {
                    title = report.getName();
                }
                if (connectionId == null) {
                    connectionId = report.getConnectionId();
                }
            }
        }

        if (connectionId == null || !StringUtils.hasText(sql)) {
            throw new IllegalArgumentException("Report query is required for PDF export");
        }

        return exportPdf(connectionId, sql, resolveTitle(title, "Report"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRows(Map<String, Object> result) {
        Object rows = result.get("rows");
        if (rows instanceof List) {
            return (List<Map<String, Object>>) rows;
        }
        return List.of(result);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(String config) {
        if (!StringUtils.hasText(config)) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(config, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse report config", e);
            return Map.of();
        }
    }

    private boolean isMarkdownReport(Report report, Map<String, Object> config) {
        return "report".equalsIgnoreCase(report.getChartType())
                || "markdown".equalsIgnoreCase(String.valueOf(config.get("type")));
    }

    private String resolveTitle(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    private String resolveErrorMessage(Exception error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return StringUtils.hasText(current.getMessage()) ? current.getMessage() : "unknown error";
    }
}
