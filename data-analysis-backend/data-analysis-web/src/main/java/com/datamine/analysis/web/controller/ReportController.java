package com.datamine.analysis.web.controller;

import com.datamine.analysis.common.entity.Report;
import com.datamine.analysis.core.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/list")
    public ResponseEntity<List<Report>> listReports(@RequestParam Long connectionId) {
        return ResponseEntity.ok(reportService.listReports(connectionId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Report> getReport(@PathVariable Long id) {
        Report report = reportService.getReport(id);
        if (report == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(report);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Report> updateReport(@PathVariable Long id, @RequestBody Report report) {
        return ResponseEntity.ok(reportService.updateReport(id, report));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReport(@PathVariable Long id) {
        reportService.deleteReport(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping({"/generate", "/generate-chart"})
    public ResponseEntity<Map<String, Object>> generateReport(@RequestBody Map<String, Object> request) {
        Long connectionId = Long.valueOf(request.get("connectionId").toString());
        String requirement = (String) request.get("requirement");
        if (requirement == null || requirement.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", true, "message", "requirement is required"));
        }
        Map<String, Object> result = reportService.generateReportArtifact(connectionId, requirement);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(@RequestBody Map<String, Object> request) throws IOException {
        Long connectionId = Long.valueOf(request.get("connectionId").toString());
        String sql = (String) request.get("sql");
        String sheetName = (String) request.getOrDefault("sheetName", "Sheet1");

        byte[] data = reportService.exportExcel(connectionId, sql, sheetName);
        String filename = URLEncoder.encode(sheetName + ".xlsx", StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @PostMapping("/export/pdf")
    public ResponseEntity<byte[]> exportPdf(@RequestBody Map<String, Object> request) throws IOException {
        Long reportId = request.get("reportId") != null
                ? Long.valueOf(request.get("reportId").toString())
                : null;
        Long connectionId = request.get("connectionId") != null
                ? Long.valueOf(request.get("connectionId").toString())
                : null;
        String sql = (String) request.get("sql");
        String title = (String) request.getOrDefault("title", "Report");

        byte[] data = reportService.exportPdf(reportId, connectionId, sql, title);
        String filename = URLEncoder.encode(title + ".pdf", StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.APPLICATION_PDF)
                .body(data);
    }
}
