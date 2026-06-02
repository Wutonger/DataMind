package com.datamine.analysis.web.controller;

import com.datamine.analysis.common.dto.ChatRequest;
import com.datamine.analysis.common.entity.ChatSession;
import com.datamine.analysis.core.service.ChatService;
import com.datamine.analysis.core.service.ConnectionAccessService;
import com.datamine.analysis.core.service.CurrentUserService;
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
    private final CurrentUserService currentUserService;
    private final ConnectionAccessService connectionAccessService;

    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> sendMessage(@RequestBody ChatRequest request) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return Flux.just("{\"error\": \"message is required\"}");
        }
        if (request.getConnectionId() == null) {
            return Flux.just("{\"error\": \"connectionId is required\"}");
        }
        Long userId = currentUserService.getRequiredUserId();
        boolean admin = currentUserService.isAdmin();
        connectionAccessService.checkConnectionAccessible(userId, admin, request.getConnectionId());
        return chatService.chatStream(userId, request.getSessionId(), request.getConnectionId(), request.getMessage());
    }

    @GetMapping("/history/{sessionId}")
    public ResponseEntity<List<Map<String, Object>>> getHistory(@PathVariable String sessionId) {
        Long userId = currentUserService.getRequiredUserId();
        return ResponseEntity.ok(chatService.getHistoryWithSteps(userId, sessionId));
    }

    @DeleteMapping("/history/{sessionId}")
    public ResponseEntity<Void> clearHistory(@PathVariable String sessionId) {
        Long userId = currentUserService.getRequiredUserId();
        chatService.clearHistory(userId, sessionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSession>> listSessions(@RequestParam Long connectionId) {
        Long userId = currentUserService.getRequiredUserId();
        boolean admin = currentUserService.isAdmin();
        connectionAccessService.checkConnectionAccessible(userId, admin, connectionId);
        return ResponseEntity.ok(chatService.listSessions(userId, connectionId));
    }

    @PostMapping("/compress/{sessionId}")
    public ResponseEntity<Map<String, Object>> compressHistory(@PathVariable String sessionId) {
        Long userId = currentUserService.getRequiredUserId();
        var result = chatService.compressHistory(userId, sessionId);
        return ResponseEntity.ok(Map.of(
                "compressed", result.compressed(),
                "message", result.message(),
                "beforeCount", result.beforeCount(),
                "afterCount", result.afterCount()
        ));
    }
}
