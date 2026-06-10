package com.datamine.analysis.core.service;

import com.datamine.analysis.common.dto.AiConfigDTO;
import com.datamine.analysis.common.entity.AppConfig;
import com.datamine.analysis.common.repository.AppConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Optional;

import org.springframework.util.StringUtils;

@Slf4j
@Service
public class AiConfigService {

    private static final String AI_CONFIG_KEY = "ai.model.default";
    private static final int DEFAULT_MAX_CONTEXT_TOKENS = 128000;

    private final AppConfigRepository appConfigRepository;
    private final ObjectMapper objectMapper;

    private AiConfigDTO cachedConfig;

    public AiConfigService(AppConfigRepository appConfigRepository, ObjectMapper objectMapper) {
        this.appConfigRepository = appConfigRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        loadAiConfig();
    }

    public void loadAiConfig() {
        try {
            Optional<AppConfig> configOpt = appConfigRepository.findByConfigKey(AI_CONFIG_KEY);
            if (configOpt.isPresent()) {
                cachedConfig = objectMapper.readValue(configOpt.get().getConfigValue(), AiConfigDTO.class);
                boolean changed = false;
                if (!StringUtils.hasText(cachedConfig.getEmbeddingModel())) {
                    cachedConfig.setEmbeddingModel("text-embedding-3-small");
                    changed = true;
                }
                if (!Boolean.TRUE.equals(cachedConfig.getReasoningEnabled())) {
                    cachedConfig.setReasoningEnabled(Boolean.TRUE);
                    changed = true;
                }
                if (!StringUtils.hasText(cachedConfig.getReasoningEffort())) {
                    cachedConfig.setReasoningEffort("medium");
                    changed = true;
                }
                if (cachedConfig.getMaxContextTokens() == null || cachedConfig.getMaxContextTokens() <= 0) {
                    cachedConfig.setMaxContextTokens(DEFAULT_MAX_CONTEXT_TOKENS);
                    changed = true;
                }
                if (changed) {
                    saveAiConfig(cachedConfig);
                }
                log.info("AI config loaded: provider={}, model={}", cachedConfig.getProvider(), cachedConfig.getModel());
            } else {
                cachedConfig = getDefaultConfig();
                saveAiConfig(cachedConfig);
                log.info("AI config created with default values");
            }
        } catch (Exception e) {
            log.error("Failed to load AI config", e);
            cachedConfig = getDefaultConfig();
        }
    }

    public AiConfigDTO getAiConfig() {
        return cachedConfig;
    }

    public AiConfigDTO updateAiConfig(AiConfigDTO config) {
        if (!StringUtils.hasText(config.getEmbeddingModel())) {
            config.setEmbeddingModel("text-embedding-3-small");
        }
        config.setReasoningEnabled(Boolean.TRUE);
        if (!StringUtils.hasText(config.getReasoningEffort())) {
            config.setReasoningEffort("medium");
        }
        if (config.getMaxContextTokens() == null || config.getMaxContextTokens() <= 0) {
            config.setMaxContextTokens(DEFAULT_MAX_CONTEXT_TOKENS);
        }
        cachedConfig = config;
        saveAiConfig(config);
        return cachedConfig;
    }

    private void saveAiConfig(AiConfigDTO config) {
        try {
            String json = objectMapper.writeValueAsString(config);
            Optional<AppConfig> configOpt = appConfigRepository.findByConfigKey(AI_CONFIG_KEY);
            AppConfig appConfig;
            if (configOpt.isPresent()) {
                appConfig = configOpt.get();
                appConfig.setConfigValue(json);
            } else {
                appConfig = new AppConfig();
                appConfig.setConfigKey(AI_CONFIG_KEY);
                appConfig.setConfigValue(json);
                appConfig.setDescription("默认AI模型配置");
            }
            appConfigRepository.save(appConfig);
            log.info("AI config saved successfully");
        } catch (Exception e) {
            log.error("Failed to save AI config", e);
            throw new RuntimeException("保存AI配置失败", e);
        }
    }

    private AiConfigDTO getDefaultConfig() {
        AiConfigDTO config = new AiConfigDTO();
        config.setProvider("openai");
        config.setBaseUrl("https://api.openai.com/v1");
        config.setApiKey("");
        config.setModel("gpt-4o");
        config.setEmbeddingModel("text-embedding-3-small");
        config.setTemperature(0.7);
        config.setReasoningEnabled(Boolean.TRUE);
        config.setReasoningEffort("medium");
        config.setMaxContextTokens(DEFAULT_MAX_CONTEXT_TOKENS);
        return config;
    }
}
