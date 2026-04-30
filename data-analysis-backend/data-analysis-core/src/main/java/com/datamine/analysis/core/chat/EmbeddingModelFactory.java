package com.datamine.analysis.core.chat;

import com.datamine.analysis.common.dto.AiConfigDTO;
import com.datamine.analysis.core.service.AiConfigService;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingModelFactory {

    private final AiConfigService aiConfigService;
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

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(15));
        requestFactory.setReadTimeout(Duration.ofSeconds(180));

        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(requestFactory);

        HttpClient reactiveHttpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15_000)
                .responseTimeout(Duration.ofSeconds(180))
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(180, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(180, TimeUnit.SECONDS)));

        WebClient.Builder webClientBuilder = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(reactiveHttpClient));

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder)
                .build();

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
