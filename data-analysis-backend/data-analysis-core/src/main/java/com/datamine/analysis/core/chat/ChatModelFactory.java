package com.datamine.analysis.core.chat;

import com.datamine.analysis.common.dto.AiConfigDTO;
import com.datamine.analysis.common.entity.AppConfig;
import com.datamine.analysis.common.repository.AppConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatModelFactory {

    private static final String AI_CONFIG_KEY = "ai.model.default";
    private static final String MISSING_MODEL_CONFIG_MESSAGE = "没有配置完整的模型信息，请先在系统设置中完成模型名称、Base URL 和 API Key 配置";

    private final AppConfigRepository appConfigRepository;
    private final ObjectMapper objectMapper;
    private final OpenAiApiFactory openAiApiFactory;
    private final Map<String, AiRuntime> runtimeCache = new ConcurrentHashMap<>();

    public ChatModel getChatModel() {
        return getAiRuntime().chatModel();
    }

    public OpenAiChatOptions getChatOptions() {
        return OpenAiChatOptions.fromOptions(getAiRuntime().chatOptions());
    }

    public void refresh() {
        runtimeCache.clear();
        log.info("Chat runtime cache cleared, will recreate on next request");
    }

    private AiRuntime getAiRuntime() {
        AppConfig config = appConfigRepository.findByConfigKey(AI_CONFIG_KEY).orElseThrow(() -> new IllegalStateException(MISSING_MODEL_CONFIG_MESSAGE));
        if (!StringUtils.hasText(config.getConfigValue())) {
            throw new IllegalStateException(MISSING_MODEL_CONFIG_MESSAGE);
        }

        String cacheKey = config.getConfigValue();
        return runtimeCache.computeIfAbsent(cacheKey, key -> createAiRuntime(config));
    }

    @SuppressWarnings("unchecked")
    private AiRuntime createAiRuntime(AppConfig config) {
        try {
            Map<String, Object> valueMap = objectMapper.readValue(config.getConfigValue(), Map.class);
            AiConfigDTO aiConfig = objectMapper.convertValue(valueMap, AiConfigDTO.class);
            validateRequiredConfig(aiConfig);

            String baseUrl = aiConfig.getBaseUrl().trim();
            String apiKey = aiConfig.getApiKey().trim();
            String model = aiConfig.getModel().trim();
            double temperature = aiConfig.getTemperature() != null ? aiConfig.getTemperature() : 0.7D;
            OpenAiApi api = openAiApiFactory.create(baseUrl, apiKey);

            OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                    .model(model)
                    .temperature(temperature);

            if (Boolean.TRUE.equals(aiConfig.getReasoningEnabled())) {
                if (!StringUtils.hasText(aiConfig.getReasoningEffort())) {
                    throw new IllegalStateException("已开启深度思考，请先选择思考强度");
                }
                optionsBuilder.reasoningEffort(aiConfig.getReasoningEffort().trim());
            }

            OpenAiChatOptions options = optionsBuilder.build();

            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .openAiApi(api)
                    .defaultOptions(options)
                    .build();

            log.info("Created ChatModel runtime: baseUrl={}, model={}, temperature={}, reasoningEnabled={}, reasoningEffort={}",
                    baseUrl,
                    model,
                    temperature,
                    Boolean.TRUE.equals(aiConfig.getReasoningEnabled()),
                    aiConfig.getReasoningEffort());
            return new AiRuntime(chatModel, options, Boolean.TRUE.equals(aiConfig.getReasoningEnabled()));
        } catch (Exception e) {
            if (e instanceof IllegalStateException) {
                throw (IllegalStateException) e;
            }
            throw new RuntimeException("Failed to create ChatModel runtime", e);
        }
    }

    private void validateRequiredConfig(AiConfigDTO aiConfig) {
        if (aiConfig == null
                || !StringUtils.hasText(aiConfig.getModel())
                || !StringUtils.hasText(aiConfig.getBaseUrl())
                || !StringUtils.hasText(aiConfig.getApiKey())) {
            throw new IllegalStateException(MISSING_MODEL_CONFIG_MESSAGE);
        }
    }

    public boolean isReasoningEnabled() {
        return getAiRuntime().reasoningEnabled();
    }

    private record AiRuntime(ChatModel chatModel, OpenAiChatOptions chatOptions, boolean reasoningEnabled) {
    }
}
