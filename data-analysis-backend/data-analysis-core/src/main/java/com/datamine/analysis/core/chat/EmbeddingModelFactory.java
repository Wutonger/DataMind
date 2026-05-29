package com.datamine.analysis.core.chat;

import com.datamine.analysis.common.dto.AiConfigDTO;
import com.datamine.analysis.core.service.AiConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingModelFactory {

    private final AiConfigService aiConfigService;
    private final OpenAiApiFactory openAiApiFactory;
    private final Map<String, EmbeddingModel> modelCache = new ConcurrentHashMap<>();

    public EmbeddingModel getEmbeddingModel() {
        AiConfigDTO config = aiConfigService.getAiConfig();
        String cacheKey = String.join("|",
                defaultString(config.getBaseUrl()),
                defaultString(config.getApiKey()),
                defaultString(resolveEmbeddingModel(config)));
        return modelCache.computeIfAbsent(cacheKey, key -> createEmbeddingModel(config));
    }

    public void refresh() {
        modelCache.clear();
        log.info("EmbeddingModel cache cleared");
    }

    private EmbeddingModel createEmbeddingModel(AiConfigDTO config) {
        String baseUrl = StringUtils.hasText(config.getBaseUrl()) ? config.getBaseUrl() : "https://api.openai.com/v1";
        String apiKey = defaultString(config.getApiKey());
        String model = resolveEmbeddingModel(config);
        OpenAiApi api = openAiApiFactory.create(baseUrl, apiKey);

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(model)
                .build();

        log.info("Created EmbeddingModel: baseUrl={}, model={}", baseUrl, model);
        return new OpenAiEmbeddingModel(api, MetadataMode.NONE, options);
    }

    private String resolveEmbeddingModel(AiConfigDTO config) {
        if (StringUtils.hasText(config.getEmbeddingModel())) {
            return config.getEmbeddingModel();
        }
        return OpenAiApi.DEFAULT_EMBEDDING_MODEL;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
