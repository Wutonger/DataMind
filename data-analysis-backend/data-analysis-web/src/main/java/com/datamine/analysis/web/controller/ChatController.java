package com.datamine.analysis.web.controller;

import com.datamine.analysis.common.dto.ChatRequest;
import com.datamine.analysis.common.entity.ChatSession;
import com.datamine.analysis.core.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> sendMessage(@RequestBody ChatRequest request) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return Flux.just("{\"error\": \"message is required\"}");
        }
        return chatService.chatStream(request.getSessionId(), request.getConnectionId(), request.getMessage());
    }

    @GetMapping("/history/{sessionId}")
    public ResponseEntity<List<Map<String, Object>>> getHistory(@PathVariable String sessionId) {
        List<Map<String, Object>> history = chatService.getHistoryWithSteps(sessionId);
        return ResponseEntity.ok(history);
    }

    @DeleteMapping("/history/{sessionId}")
    public ResponseEntity<Void> clearHistory(@PathVariable String sessionId) {
        chatService.clearHistory(sessionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSession>> listSessions(@RequestParam Long connectionId) {
        List<ChatSession> sessions = chatService.listSessions(connectionId);
        return ResponseEntity.ok(sessions);
    }

    @PostMapping("/compress/{sessionId}")
    public ResponseEntity<Map<String, Object>> compressHistory(@PathVariable String sessionId) {
        var result = chatService.compressHistory(sessionId);
        return ResponseEntity.ok(Map.of(
                "compressed", result.compressed(),
                "message", result.message(),
                "beforeCount", result.beforeCount(),
                "afterCount", result.afterCount()
        ));
    }
}
