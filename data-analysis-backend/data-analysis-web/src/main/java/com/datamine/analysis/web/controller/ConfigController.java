package com.datamine.analysis.web.controller;

import com.datamine.analysis.common.dto.AiConfigDTO;
import com.datamine.analysis.core.chat.EmbeddingModelFactory;
import com.datamine.analysis.core.service.AiConfigService;
import com.datamine.analysis.core.service.ChatService;
import com.datamine.analysis.core.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class ConfigController {

    private final AiConfigService aiConfigService;
    private final ChatService chatService;
    private final EmbeddingModelFactory embeddingModelFactory;
    private final CurrentUserService currentUserService;

    @GetMapping("/ai")
    public ResponseEntity<AiConfigDTO> getAiConfig() {
        currentUserService.checkAdmin();
        return ResponseEntity.ok(aiConfigService.getAiConfig());
    }

    @PutMapping("/ai")
    public ResponseEntity<AiConfigDTO> updateAiConfig(@RequestBody AiConfigDTO config) {
        currentUserService.checkAdmin();
        AiConfigDTO updated = aiConfigService.updateAiConfig(config);
        chatService.refreshClient();
        embeddingModelFactory.refresh();
        return ResponseEntity.ok(updated);
    }
}
