package com.datamine.analysis.web.controller;

import com.datamine.analysis.common.entity.Report;
import com.datamine.analysis.core.service.ConnectionAccessService;
import com.datamine.analysis.core.service.CurrentUserService;
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
    private final CurrentUserService currentUserService;
    private final ConnectionAccessService connectionAccessService;

    @GetMapping("/list")
    public ResponseEntity<List<Report>> listReports(@RequestParam Long connectionId) {
        Long userId = currentUserService.getRequiredUserId();
        boolean admin = currentUserService.isAdmin();
        connectionAccessService.checkConnectionAccessible(userId, admin, connectionId);
        return ResponseEntity.ok(reportService.listReports(userId, connectionId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Report> getReport(@PathVariable Long id) {
        Long userId = currentUserService.getRequiredUserId();
        Report report = reportService.getReport(userId, id);
        if (report == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(report);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Report> updateReport(@PathVariable Long id, @RequestBody Report report) {
        Long userId = currentUserService.getRequiredUserId();
        return ResponseEntity.ok(reportService.updateReport(userId, id, report));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReport(@PathVariable Long id) {
        Long userId = currentUserService.getRequiredUserId();
        reportService.deleteReport(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping({"/generate", "/generate-chart"})
    public ResponseEntity<Map<String, Object>> generateReport(@RequestBody Map<String, Object> request) {
        Long connectionId = Long.valueOf(request.get("connectionId").toString());
        String requirement = (String) request.get("requirement");
        if (requirement == null || requirement.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", true, "message", "requirement is required"));
        }
        Long userId = currentUserService.getRequiredUserId();
        boolean admin = currentUserService.isAdmin();
        connectionAccessService.checkConnectionAccessible(userId, admin, connectionId);
        return ResponseEntity.ok(reportService.generateReportArtifact(userId, connectionId, requirement));
    }

    @PostMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(@RequestBody Map<String, Object> request) throws IOException {
        Long connectionId = Long.valueOf(request.get("connectionId").toString());
        Long userId = currentUserService.getRequiredUserId();
        boolean admin = currentUserService.isAdmin();
        connectionAccessService.checkConnectionAccessible(userId, admin, connectionId);

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
        Long userId = currentUserService.getRequiredUserId();
        Long reportId = request.get("reportId") != null
                ? Long.valueOf(request.get("reportId").toString())
                : null;
        Long connectionId = request.get("connectionId") != null
                ? Long.valueOf(request.get("connectionId").toString())
                : null;
        if (connectionId != null) {
            boolean admin = currentUserService.isAdmin();
            connectionAccessService.checkConnectionAccessible(userId, admin, connectionId);
        }
        String sql = (String) request.get("sql");
        String title = (String) request.getOrDefault("title", "Report");

        byte[] data = reportService.exportPdf(userId, reportId, connectionId, sql, title);
        String filename = URLEncoder.encode(title + ".pdf", StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.APPLICATION_PDF)
                .body(data);
    }
}
