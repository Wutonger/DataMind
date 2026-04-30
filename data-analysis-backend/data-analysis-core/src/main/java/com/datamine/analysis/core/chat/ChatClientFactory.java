package com.datamine.analysis.core.chat;

import com.datamine.analysis.common.entity.AppConfig;
import com.datamine.analysis.common.repository.AppConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatClientFactory {

    private final AppConfigRepository appConfigRepository;
    private final ObjectMapper objectMapper;
    private final Map<String, AiRuntime> runtimeCache = new ConcurrentHashMap<>();

    public ChatClient getChatClient() {
        return getAiRuntime().chatClient();
    }

    private AiRuntime getAiRuntime() {
        AppConfig config = appConfigRepository.findByConfigKey("ai.model.default")
                .orElseThrow(() -> new RuntimeException("AI config not found"));

        String cacheKey = config.getConfigValue();
        return runtimeCache.computeIfAbsent(cacheKey, k -> createAiRuntime(config));
    }

    public void refreshClient() {
        runtimeCache.clear();
        log.info("ChatClient cache cleared, will recreate on next request");
    }

    @SuppressWarnings("unchecked")
    private AiRuntime createAiRuntime(AppConfig config) {
        try {
            Map<String, Object> valueMap = objectMapper.readValue(config.getConfigValue(), Map.class);
            String baseUrl = String.valueOf(valueMap.getOrDefault("baseUrl", "https://api.openai.com/v1"));
            String apiKey = String.valueOf(valueMap.getOrDefault("apiKey", ""));
            String model = String.valueOf(valueMap.getOrDefault("model", "gpt-4o"));
            double temperature = valueMap.containsKey("temperature")
                    ? ((Number) valueMap.get("temperature")).doubleValue()
                    : 0.7;

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

            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(model)
                    .temperature(temperature)
                    .build();

            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .openAiApi(api)
                    .defaultOptions(options)
                    .build();

            log.info("Created ChatClient: baseUrl={}, model={}, temperature={}", baseUrl, model, temperature);
            return new AiRuntime(ChatClient.builder(chatModel).build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create ChatClient", e);
        }
    }

    private record AiRuntime(ChatClient chatClient) {
    }
}
