package com.datamine.analysis.web.controller;

import com.datamine.analysis.common.entity.TableMetadata;
import com.datamine.analysis.core.schema.TableScanner;
import com.datamine.analysis.core.service.ConnectionAccessService;
import com.datamine.analysis.core.service.CurrentUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/tables")
@RequiredArgsConstructor
public class TableAnalysisController {

    private final TableScanner tableScanner;
    private final ObjectMapper objectMapper;
    private final CurrentUserService currentUserService;
    private final ConnectionAccessService connectionAccessService;
    private final AsyncTaskExecutor scanTaskExecutor = new SimpleAsyncTaskExecutor("table-scan-");

    @GetMapping(value = "/scan/{connectionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> scanTables(@PathVariable Long connectionId) {
        Long userId = currentUserService.getRequiredUserId();
        boolean admin = currentUserService.isAdmin();
        connectionAccessService.checkConnectionAccessible(userId, admin, connectionId);
        SseEmitter emitter = new SseEmitter(0L);
        scanTaskExecutor.execute(() -> streamTableScan(userId, connectionId, emitter));

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }

    @GetMapping("/metadata/{connectionId}")
    public ResponseEntity<List<TableMetadata>> getMetadata(@PathVariable Long connectionId) {
        Long userId = currentUserService.getRequiredUserId();
        boolean admin = currentUserService.isAdmin();
        connectionAccessService.checkConnectionAccessible(userId, admin, connectionId);
        return ResponseEntity.ok(tableScanner.getCachedMetadata(connectionId));
    }

    private void streamTableScan(Long userId, Long connectionId, SseEmitter emitter) {
        try {
            List<TableMetadata> tables = tableScanner.scanTables(userId, connectionId, event ->
                    trySendEvent(emitter, event.type(), event.data()));

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("tables", tables);
            payload.put("totalTables", tables.size());
            payload.put("processedTables", tables.size());
            payload.put("percent", 100);
            payload.put("message", "扫描完成，共更新 " + tables.size() + " 张表");
            trySendEvent(emitter, "SCAN_COMPLETED", payload);
            emitter.complete();
        } catch (Exception e) {
            log.error("Table scan failed. connectionId={}", connectionId, e);
            trySendEvent(emitter, "SCAN_FAILED", Map.of(
                    "message", summarizeError(e)
            ));
            emitter.complete();
        }
    }

    /**
     * 使用 SseEmitter 主动发送并立即刷新事件，避免进度在响应结束前被缓冲。
     */
    private void trySendEvent(SseEmitter emitter, String type, Map<String, Object> data) {
        try {
            emitter.send(SseEmitter.event().data(toEventPayload(type, data)));
        } catch (Exception e) {
            log.debug("Failed to send table scan event. type={}", type, e);
        }
    }

    private String toEventPayload(String type, Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(Map.of("type", type, "data", data));
        } catch (Exception e) {
            log.error("Failed to serialize table scan event. type={}", type, e);
            return "{\"type\":\"SCAN_FAILED\",\"data\":{\"message\":\"事件序列化失败\"}}";
        }
    }

    private String summarizeError(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current != null ? current.getMessage() : error.getMessage();
        return message == null || message.isBlank() ? "表扫描失败" : message;
    }
}
