package com.datamine.analysis.web.controller;

import com.datamine.analysis.common.entity.SqlHistory;
import com.datamine.analysis.core.service.ConnectionAccessService;
import com.datamine.analysis.core.service.CurrentUserService;
import com.datamine.analysis.core.service.SqlFormatService;
import com.datamine.analysis.core.service.SqlHistoryService;
import com.datamine.analysis.core.service.SqlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sql")
@RequiredArgsConstructor
public class SqlController {

    private final SqlService sqlService;
    private final SqlFormatService sqlFormatService;
    private final SqlHistoryService sqlHistoryService;
    private final CurrentUserService currentUserService;
    private final ConnectionAccessService connectionAccessService;

    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> executeSql(@RequestBody Map<String, Object> request) {
        Long connectionId = Long.valueOf(request.get("connectionId").toString());
        String sql = (String) request.get("sql");
        if (sql == null || sql.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "sql is required"));
        }
        Long userId = currentUserService.getRequiredUserId();
        boolean admin = currentUserService.isAdmin();
        connectionAccessService.checkConnectionAccessible(userId, admin, connectionId);
        return ResponseEntity.ok(sqlService.executeQuery(connectionId, sql));
    }

    @PostMapping("/generate")
    public ResponseEntity<Map<String, String>> generateSql(@RequestBody Map<String, Object> request) {
        Object rawConnectionId = request.get("connectionId");
        if (rawConnectionId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "connectionId is required"));
        }

        Long connectionId = Long.valueOf(rawConnectionId.toString());
        String question = request.get("question") != null ? request.get("question").toString() : null;
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "question is required"));
        }

        Long userId = currentUserService.getRequiredUserId();
        boolean admin = currentUserService.isAdmin();
        connectionAccessService.checkConnectionAccessible(userId, admin, connectionId);

        try {
            Map<String, String> result = sqlService.generateSql(userId, connectionId, question);
            String sql = result.get("sql");
            if (sql != null && !sql.isBlank()) {
                String sessionId = request.get("sessionId") != null ? request.get("sessionId").toString() : null;
                sqlHistoryService.recordGenerated(userId, connectionId, sessionId, sql, question);
            }
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/format")
    public ResponseEntity<Map<String, String>> formatSql(@RequestBody Map<String, String> request) {
        String sql = request.get("sql");
        if (sql == null || sql.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "sql is required"));
        }
        return ResponseEntity.ok(Map.of("sql", sqlFormatService.format(sql)));
    }

    @GetMapping("/history/{connectionId}")
    public ResponseEntity<List<SqlHistory>> getHistory(@PathVariable Long connectionId) {
        Long userId = currentUserService.getRequiredUserId();
        boolean admin = currentUserService.isAdmin();
        connectionAccessService.checkConnectionAccessible(userId, admin, connectionId);
        return ResponseEntity.ok(sqlHistoryService.getRecentByConnectionId(userId, connectionId));
    }

    @DeleteMapping("/history/{connectionId}/{historyId}")
    public ResponseEntity<Map<String, Object>> deleteHistory(@PathVariable Long connectionId,
                                                             @PathVariable Long historyId) {
        Long userId = currentUserService.getRequiredUserId();
        boolean admin = currentUserService.isAdmin();
        connectionAccessService.checkConnectionAccessible(userId, admin, connectionId);
        try {
            sqlHistoryService.deleteByConnectionId(userId, connectionId, historyId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
