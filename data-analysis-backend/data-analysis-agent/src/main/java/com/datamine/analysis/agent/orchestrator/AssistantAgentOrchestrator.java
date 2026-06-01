package com.datamine.analysis.agent.orchestrator;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.datamine.analysis.agent.interceptor.StepTrackingToolInterceptor;
import com.datamine.analysis.agent.model.ChatExecutionResult;
import com.datamine.analysis.agent.model.ReportExecutionResult;
import com.datamine.analysis.agent.model.SqlExecutionResult;
import com.datamine.analysis.agent.model.ToolExecutionRecord;
import com.datamine.analysis.agent.prompt.PromptConstant;
import com.datamine.analysis.agent.tool.*;
import com.datamine.analysis.agent.workflow.WorkflowRunTracker;
import com.datamine.analysis.common.dto.knowledge.KnowledgeCitationDTO;
import com.datamine.analysis.skills.tool.ToolStateKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
/**
 * 统一封装 AssistantAgent 的构建、执行与执行链路记录。
 * 聊天、SQL、图表和结构化分析都从这里进入。
 */
public class AssistantAgentOrchestrator {

    public static final String ASSISTANT_AGENT = "assistant";
    public static final String ASSISTANT_OWNER = "AssistantAgent";
    public static final String ROUTE_MODE = "SINGLE_AGENT";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final AgentToolsetFactory agentToolsetFactory;
    private final ToolProgressResolver toolProgressResolver;
    private final ToolResultProcessor toolResultProcessor;
    private final WorkflowRunTracker workflowRunTracker;

    /**
     * 处理聊天场景的流式输出，并在结束时返回完整执行结果。
     */
    public Flux<String> orchestrateStream(String conversationId,
                                          Long connectionId,
                                          String userInput,
                                          ChatModel chatModel,
                                          ChatMemory chatMemory,
                                          boolean reasoningEnabled,
                                          Consumer<ChatExecutionResult> completionCallback) {
        return Flux.create(emitter -> {
            List<Map<String, Object>> executedSteps = Collections.synchronizedList(new ArrayList<>());
            List<KnowledgeCitationDTO> collectedCitations = Collections.synchronizedList(new ArrayList<>());
            AtomicReference<OverAllState> latestState = new AtomicReference<>();
            AtomicReference<ChatProcessStepTracker> chatProcessStepTrackerRef = new AtomicReference<>();
            StringBuilder responseBuilder = new StringBuilder();
            StringBuilder reasoningBuilder = new StringBuilder();
            String workflowRunId = workflowRunTracker.startRun("chat", connectionId, summarizeTitle(userInput));
            String setupStepId = workflowRunId + "-setup";
            String finalStepId = workflowRunId + "-final";

            Consumer<String> eventConsumer = event -> {
                if (!emitter.isCancelled()) {
                    emitter.next(event);
                }
            };

            try {
                workflowRunTracker.startStep(
                        workflowRunId,
                        setupStepId,
                        ASSISTANT_AGENT,
                        ASSISTANT_OWNER,
                        "准备执行任务",
                        "Execution Setup",
                        summarizeText(userInput),
                        List.of()
                );
                workflowRunTracker.addTimeline(workflowRunId, setupStepId, ASSISTANT_OWNER, "开始准备执行链路");
                eventConsumer.accept(toJsonEvent("WORKFLOW_STARTED", Map.of(
                        "runId", workflowRunId,
                        "sessionId", conversationId,
                        "scene", "chat",
                        "title", summarizeTitle(userInput),
                        "reasoningEnabled", reasoningEnabled
                )));
                // 聊天过程中的“思考 / 整理回答”单独由过程跟踪器负责，避免前端继续猜测状态。
                ChatProcessStepTracker chatProcessStepTracker = new ChatProcessStepTracker(
                        workflowRunId,
                        ASSISTANT_AGENT,
                        ASSISTANT_OWNER,
                        workflowRunTracker,
                        executedSteps,
                        eventConsumer,
                        this::toJsonEvent
                );
                chatProcessStepTrackerRef.set(chatProcessStepTracker);
                chatProcessStepTracker.startInitialThinking();

                List<Message> inputMessages = loadConversationMessages(conversationId, userInput, chatMemory);
                Agent assistantAgent = buildChatAgent(
                        connectionId,
                        userInput,
                        chatModel,
                        reasoningEnabled,
                        eventConsumer,
                        executedSteps,
                        collectedCitations,
                        workflowRunId,
                        chatProcessStepTracker
                );

                RunnableConfig runnableConfig = RunnableConfig.builder()
                        .threadId(conversationId)
                        .addStateUpdate(Map.of(ToolStateKeys.CURRENT_WORKFLOW_RUN_ID, workflowRunId))
                        .build();

                log.info("Starting chat workflow. sessionId={}, connectionId={}", conversationId, connectionId);

                Disposable disposable = assistantAgent.stream(inputMessages, runnableConfig)
                        .doOnNext(nodeOutput -> latestState.set(nodeOutput.state()))
                        .subscribe(nodeOutput -> handleNodeOutput(
                                        nodeOutput,
                                        responseBuilder,
                                        reasoningBuilder,
                                        reasoningEnabled,
                                        chatProcessStepTracker,
                                        eventConsumer),
                                error -> {
                                    String message = summarizeError(error);
                                    List<Map<String, Object>> finalSteps = copySteps(executedSteps);
                                    List<KnowledgeCitationDTO> finalCitations = copyCitations(collectedCitations);
                                    ChatProcessStepTracker tracker = chatProcessStepTrackerRef.get();
                                    if (tracker != null) {
                                        tracker.failActiveStep(message);
                                    }
                                    log.error("Chat workflow failed. sessionId={}", conversationId, error);
                                    eventConsumer.accept(toJsonEvent("WORKFLOW_COMPLETED", Map.of(
                                            "runId", workflowRunId,
                                            "sessionId", conversationId,
                                            "scene", "chat",
                                            "status", "FAILED",
                                            "routeMode", ROUTE_MODE
                                    )));
                                    eventConsumer.accept(toJsonEvent("ERROR", Map.of(
                                            "message", message,
                                            "sessionId", conversationId
                                    )));
                                    emitter.complete();
                                    persistChatFailureAsync(
                                            workflowRunId,
                                            setupStepId,
                                            conversationId,
                                            message,
                                            finalSteps,
                                            finalCitations,
                                            completionCallback
                                    );
                                },
                                () -> {
                                    // 聊天场景优先以真实流式输出为准，避免结束时被 state 中较晚但不完整的消息覆盖。
                                    String fullResponse = StringUtils.hasText(responseBuilder)
                                            ? responseBuilder.toString()
                                            : extractFinalResponse(latestState.get()).orElse("");
                                    String reasoning = reasoningBuilder.toString().trim();

                                    if (!StringUtils.hasText(fullResponse)) {
                                        fullResponse = "抱歉，我暂时没有生成有效回复。";
                                    }

                                    String inputSummary = summarizeText(userInput);
                                    String outputSummary = summarizeText(fullResponse);
                                    List<KnowledgeCitationDTO> finalCitations = copyCitations(collectedCitations);
                                    chatProcessStepTracker.recordFinalizingStep(finalStepId, outputSummary);
                                    List<Map<String, Object>> finalSteps = copySteps(executedSteps);
                                    log.info("Chat workflow completed. sessionId={}, responseLength={}, citations={}",
                                            conversationId, fullResponse.length(), finalCitations.size());
                                    eventConsumer.accept(toJsonEvent("WORKFLOW_COMPLETED", Map.of(
                                            "runId", workflowRunId,
                                            "sessionId", conversationId,
                                            "scene", "chat",
                                            "status", "COMPLETED",
                                            "routeMode", ROUTE_MODE
                                    )));
                                    eventConsumer.accept(toJsonEvent("FINAL_RESPONSE", Map.of(
                                            "content", fullResponse,
                                            "reasoning", reasoning,
                                            "reasoningEnabled", reasoningEnabled,
                                            "citations", finalCitations,
                                            "workflowRunId", workflowRunId,
                                            "sessionId", conversationId
                                    )));
                                    emitter.complete();
                                    persistChatCompletionAsync(
                                            workflowRunId,
                                            setupStepId,
                                            finalStepId,
                                            conversationId,
                                            inputSummary,
                                            outputSummary,
                                            fullResponse,
                                            finalSteps,
                                            reasoning,
                                            finalCitations,
                                            chatProcessStepTracker,
                                            completionCallback
                                    );
                                });
                emitter.onDispose(disposable);
            } catch (Exception error) {
                String message = summarizeError(error);
                List<Map<String, Object>> finalSteps = copySteps(executedSteps);
                List<KnowledgeCitationDTO> finalCitations = copyCitations(collectedCitations);
                ChatProcessStepTracker tracker = chatProcessStepTrackerRef.get();
                if (tracker != null) {
                    tracker.failActiveStep(message);
                }
                log.error("Chat workflow setup failed. sessionId={}", conversationId, error);
                eventConsumer.accept(toJsonEvent("WORKFLOW_COMPLETED", Map.of(
                        "runId", workflowRunId,
                        "sessionId", conversationId,
                        "scene", "chat",
                        "status", "FAILED",
                        "routeMode", ROUTE_MODE
                )));
                eventConsumer.accept(toJsonEvent("ERROR", Map.of(
                        "message", message,
                        "sessionId", conversationId
                )));
                emitter.complete();
                persistChatFailureAsync(
                        workflowRunId,
                        setupStepId,
                        conversationId,
                        message,
                        finalSteps,
                        finalCitations,
                        completionCallback
                );
            }
        });
    }

    /**
     * 处理 SQL 生成场景，返回最终 SQL 与执行链路编号。
     */
    public SqlExecutionResult generateSql(Long connectionId,
                                          String userInput,
                                          ChatModel chatModel) {
        String workflowRunId = workflowRunTracker.startRun("sql", connectionId, summarizeTitle(userInput));
        String setupStepId = workflowRunId + "-setup";
        String finalStepId = workflowRunId + "-final";
        List<Map<String, Object>> executedSteps = java.util.Collections.synchronizedList(new ArrayList<>());
        boolean setupCompleted = false;
        boolean finalStepStarted = false;

        try {
                workflowRunTracker.startStep(
                    workflowRunId,
                    setupStepId,
                    ASSISTANT_AGENT,
                    ASSISTANT_OWNER,
                    "准备生成 SQL",
                    "Execution Setup",
                    summarizeText(userInput),
                    List.of()
            );
            workflowRunTracker.addTimeline(workflowRunId, setupStepId, ASSISTANT_OWNER, "开始准备 SQL 生成任务");

            Agent assistantAgent = buildSqlAgent(connectionId, userInput, chatModel, executedSteps, workflowRunId);

            RunnableConfig runnableConfig = RunnableConfig.builder()
                    .threadId(workflowRunId)
                    .addStateUpdate(Map.of(
                            ToolStateKeys.CURRENT_WORKFLOW_RUN_ID, workflowRunId
                    ))
                    .build();

            Optional<OverAllState> finalState = assistantAgent.invoke(List.of(new UserMessage(userInput)), runnableConfig);

            workflowRunTracker.completeStep(
                    workflowRunId,
                    setupStepId,
                    "已进入 SQL 生成阶段",
                    List.of("routeMode=" + ROUTE_MODE)
            );
            workflowRunTracker.addTimeline(workflowRunId, setupStepId, ASSISTANT_OWNER, "已进入 SQL 生成阶段");
            setupCompleted = true;

            workflowRunTracker.startStep(
                    workflowRunId,
                    finalStepId,
                    ASSISTANT_AGENT,
                    ASSISTANT_OWNER,
                    "整理最终 SQL",
                    "Final SQL",
                    summarizeText(userInput),
                    List.of("final_sql")
            );
            workflowRunTracker.addTimeline(workflowRunId, finalStepId, ASSISTANT_OWNER, "开始整理最终 SQL");
            finalStepStarted = true;

            String normalizedSql = normalizeSql(extractFinalResponse(finalState.orElse(null)).orElse(""));
            if (!StringUtils.hasText(normalizedSql)) {
                throw new IllegalStateException("未生成有效 SQL，请尝试更明确地描述查询需求");
            }

            workflowRunTracker.completeStep(
                    workflowRunId,
                    finalStepId,
                    summarizeText(normalizedSql),
                    List.of("final_sql")
            );
            workflowRunTracker.addTimeline(workflowRunId, finalStepId, ASSISTANT_OWNER, "最终 SQL 已生成");
            workflowRunTracker.completeRun(workflowRunId, ROUTE_MODE);

            log.info("SQL workflow completed. connectionId={}, workflowRunId={}", connectionId, workflowRunId);
            return new SqlExecutionResult(normalizedSql, workflowRunId);
        } catch (Exception error) {
            String message = summarizeError(error);

            if (finalStepStarted) {
                workflowRunTracker.failStep(workflowRunId, finalStepId, message, List.of("final_sql"));
            } else if (!setupCompleted) {
                workflowRunTracker.failStep(workflowRunId, setupStepId, "执行准备失败", List.of("routeMode=" + ROUTE_MODE));
            } else {
                workflowRunTracker.failStep(workflowRunId, setupStepId, message, List.of("routeMode=" + ROUTE_MODE));
            }
            workflowRunTracker.failRun(workflowRunId, ROUTE_MODE, message);
            log.error("SQL workflow failed. connectionId={}, workflowRunId={}", connectionId, workflowRunId, error);
            throw new IllegalStateException("SQL 生成失败: " + message, error);
        }
    }

    /**
     * 处理报表中心产物生成场景，返回图表或文档配置与落库信息。
     */
    public ReportExecutionResult generateReport(Long connectionId,
                                                String userRequirement,
                                                ChatModel chatModel) {
        String workflowRunId = workflowRunTracker.startRun("report", connectionId, summarizeTitle(userRequirement));
        String setupStepId = workflowRunId + "-setup";
        String finalStepId = workflowRunId + "-final";
        List<Map<String, Object>> executedSteps = java.util.Collections.synchronizedList(new ArrayList<>());
        List<ToolExecutionRecord> toolExecutions = java.util.Collections.synchronizedList(new ArrayList<>());
        boolean setupCompleted = false;
        boolean finalStepStarted = false;

        try {
            workflowRunTracker.startStep(
                    workflowRunId,
                    setupStepId,
                    ASSISTANT_AGENT,
                    ASSISTANT_OWNER,
                    "准备生成报表产物",
                    "Execution Setup",
                    summarizeText(userRequirement),
                    List.of()
            );
            workflowRunTracker.addTimeline(workflowRunId, setupStepId, ASSISTANT_OWNER, "开始准备报表产物生成任务");

            Agent assistantAgent = buildReportAgent(connectionId, userRequirement, chatModel, executedSteps, toolExecutions::add, workflowRunId);

            RunnableConfig runnableConfig = RunnableConfig.builder()
                    .threadId(workflowRunId)
                    .addStateUpdate(Map.of(
                            ToolStateKeys.CURRENT_WORKFLOW_RUN_ID, workflowRunId
                    ))
                    .build();

            Optional<OverAllState> finalState = assistantAgent.invoke(List.of(new UserMessage(userRequirement)), runnableConfig);

            workflowRunTracker.completeStep(
                    workflowRunId,
                    setupStepId,
                    "已进入报表产物生成阶段",
                    List.of("routeMode=" + ROUTE_MODE)
            );
            workflowRunTracker.addTimeline(workflowRunId, setupStepId, ASSISTANT_OWNER, "已进入报表产物生成阶段");
            setupCompleted = true;

            ToolExecutionRecord toolExecution = findLatestSuccessfulToolExecution(toolExecutions, "save_chart_report", "save_markdown_report")
                    .orElseThrow(() -> new IllegalStateException(
                            resolveReportGenerationFailureMessage(
                                    extractFinalResponse(finalState.orElse(null)).orElse("")
                            )));
            Map<String, Object> toolResponse = parseRawToolResult(toolExecution.rawResult());
            Map<String, Object> payload = extractToolData(toolResponse);

            String sql = stringValue(payload.get("sql"));
            Map<String, Object> chartConfig = castToMap(payload.get("chartConfig"));
            Map<String, Object> rawResult = castToMap(payload.get("rawResult"));
            Long reportId = toLong(payload.get("reportId"));
            String reportName = stringValue(payload.get("reportName"));
            boolean savedToReportCenter = Boolean.TRUE.equals(payload.get("savedToReportCenter"));
            String artifactType = "chart";
            Map<String, Object> artifactConfig = chartConfig;
            String finalSummary = savedToReportCenter && StringUtils.hasText(reportName)
                    ? "图表已生成并保存到报表中心：" + reportName
                    : "图表已生成";

            if (isSaveMarkdownReportToolName(toolExecution.toolName())) {
                artifactType = "report";
                artifactConfig = castToMap(payload.get("reportConfig"));
                if (artifactConfig.isEmpty()) {
                    String reportContent = stringValue(payload.get("report"));
                    if (StringUtils.hasText(reportContent)) {
                        artifactConfig = Map.of(
                                "type", "markdown",
                                "content", reportContent
                        );
                    }
                }
                chartConfig = artifactConfig;
                finalSummary = savedToReportCenter && StringUtils.hasText(reportName)
                        ? "文档报告已生成并保存到报表中心：" + reportName
                        : "文档报告已生成";
            }

            if (!StringUtils.hasText(sql) || chartConfig.isEmpty()) {
                throw new IllegalStateException("报表产物结果不完整，请稍后重试");
            }

            try {
                workflowRunTracker.startStep(
                        workflowRunId,
                        finalStepId,
                        ASSISTANT_AGENT,
                        ASSISTANT_OWNER,
                        "整理报表产物结果",
                        "Final Synthesis",
                        summarizeText(userRequirement),
                        List.of("final_chart")
                );
                workflowRunTracker.addTimeline(workflowRunId, finalStepId, ASSISTANT_OWNER, "开始整理报表产物结果");
                finalStepStarted = true;
                workflowRunTracker.completeStep(
                        workflowRunId,
                        finalStepId,
                        finalSummary,
                        List.of("final_chart")
                );
                workflowRunTracker.addTimeline(workflowRunId, finalStepId, ASSISTANT_OWNER, "报表产物结果已整理完成");
                workflowRunTracker.completeRun(workflowRunId, ROUTE_MODE);
            } catch (Exception trackerError) {
                log.warn("Report workflow tracking finalization failed. connectionId={}, workflowRunId={}, reportId={}, reason={}",
                        connectionId, workflowRunId, reportId, summarizeError(trackerError));
            }

            log.info("Report workflow completed. connectionId={}, workflowRunId={}, tool={}, reportId={}",
                    connectionId, workflowRunId, toolExecution.toolName(), reportId);
            return new ReportExecutionResult(
                    artifactType,
                    sql,
                    artifactConfig,
                    rawResult,
                    workflowRunId,
                    reportId,
                    reportName,
                    savedToReportCenter
            );
        } catch (Exception error) {
            String message = summarizeError(error);

            if (finalStepStarted) {
                workflowRunTracker.failStep(workflowRunId, finalStepId, message, List.of("final_chart"));
            } else if (!setupCompleted) {
                workflowRunTracker.failStep(workflowRunId, setupStepId, "执行准备失败", List.of("routeMode=" + ROUTE_MODE));
            } else {
                workflowRunTracker.failStep(workflowRunId, setupStepId, message, List.of("routeMode=" + ROUTE_MODE));
            }
            workflowRunTracker.failRun(workflowRunId, ROUTE_MODE, message);
            log.error("Report workflow failed. connectionId={}, workflowRunId={}", connectionId, workflowRunId, error);
            throw new IllegalStateException("报表产物生成失败: " + message, error);
        }
    }

    /**
     * 为表结构分析生成简短业务描述。
     */
    public Map<String, String> generateTableDescriptions(ChatModel chatModel,
                                                         List<Map<String, Object>> tablePayload) {
        return invokeStructuredAgent(chatModel, PromptConstant.TABLE_DESCRIPTION_PROMPT, tablePayload, new TypeReference<>() {
        });
    }

    /**
     * 根据全库结构信息输出标准化关系结果。
     */
    public Map<String, List<Map<String, Object>>> analyzeGlobalRelations(ChatModel chatModel,
                                                                         Map<String, Object> relationPayload) {
        return invokeStructuredAgent(chatModel, PromptConstant.GLOBAL_RELATION_PROMPT, relationPayload, new TypeReference<>() {
        });
    }

    private Agent buildChatAgent(Long connectionId,
                                 String userInput,
                                 ChatModel chatModel,
                                 boolean reasoningEnabled,
                                 Consumer<String> eventConsumer,
                                 List<Map<String, Object>> executedSteps,
                                 List<KnowledgeCitationDTO> collectedCitations,
                                 String workflowRunId,
                                 ChatProcessStepTracker chatProcessStepTracker) {
        AgentToolsetFactory.AgentToolset toolset = agentToolsetFactory.createChatToolset(connectionId, userInput);
        StepTrackingToolInterceptor interceptor = buildTrackingInterceptor(
                eventConsumer,
                executedSteps,
                collectedCitations,
                toolset.sceneCallbacks(),
                this::toJsonEvent,
                null,
                workflowRunId,
                chatProcessStepTracker
        );

        return buildReactAgent(
                appendCapabilityHint(PromptConstant.CHAT_AGENT_PROMPT, connectionId, toolset.isEmpty()),
                chatModel,
                toolset.baseCallbacks(),
                interceptor,
                reasoningEnabled,
                toolset.skillHook()
        );
    }

    private Agent buildSqlAgent(Long connectionId,
                                String userInput,
                                ChatModel chatModel,
                                List<Map<String, Object>> executedSteps,
                                String workflowRunId) {
        AgentToolsetFactory.AgentToolset toolset = agentToolsetFactory.createSqlToolset(connectionId, userInput);

        if (!toolset.hasSchemaTools()) {
            throw new IllegalStateException("No schema tools are available for the current connection. Please confirm MCP services are running.");
        }

        StepTrackingToolInterceptor interceptor = buildTrackingInterceptor(
                event -> {
                },
                executedSteps,
                new ArrayList<>(),
                toolset.sceneCallbacks(),
                (type, data) -> "",
                null,
                workflowRunId,
                null
        );

        return buildReactAgent(PromptConstant.SQL_AGENT_PROMPT, chatModel, toolset.baseCallbacks(), interceptor, false, toolset.skillHook());
    }

    private Agent buildReportAgent(Long connectionId,
                                   String userInput,
                                   ChatModel chatModel,
                                   List<Map<String, Object>> executedSteps,
                                   Consumer<ToolExecutionRecord> toolResultConsumer,
                                   String workflowRunId) {
        AgentToolsetFactory.AgentToolset toolset = agentToolsetFactory.createReportToolset(connectionId, userInput);

        if (!toolset.hasReportArtifactTools()) {
            throw new IllegalStateException("No report artifact tools are available. Please confirm the related skills are registered.");
        }

        StepTrackingToolInterceptor interceptor = buildTrackingInterceptor(
                event -> {
                },
                executedSteps,
                new ArrayList<>(),
                toolset.sceneCallbacks(),
                (type, data) -> "",
                toolResultConsumer,
                workflowRunId,
                null
        );

        return buildReactAgent(PromptConstant.REPORT_AGENT_PROMPT, chatModel, toolset.baseCallbacks(), interceptor, false, toolset.skillHook());
    }

    private StepTrackingToolInterceptor buildTrackingInterceptor(Consumer<String> eventConsumer,
                                                                 List<Map<String, Object>> executedSteps,
                                                                 List<KnowledgeCitationDTO> collectedCitations,
                                                                 List<ToolCallback> toolCallbacks,
                                                                 BiFunction<String, Map<String, Object>, String> eventJsonBuilder,
                                                                 Consumer<ToolExecutionRecord> toolResultConsumer,
                                                                 String workflowRunId,
                                                                 ChatProcessStepTracker chatProcessStepTracker) {
        Map<String, ToolProgressDescriptor> toolProgressDescriptors = toolProgressResolver.buildDescriptors(toolCallbacks);
        return new StepTrackingToolInterceptor(
                eventConsumer,
                executedSteps,
                collectedCitations,
                toolProgressDescriptors,
                toolProgressResolver,
                toolResultProcessor,
                eventJsonBuilder,
                toolResultConsumer,
                workflowRunTracker,
                workflowRunId,
                chatProcessStepTracker
        );
    }

    private Agent buildReactAgent(String systemPrompt,
                                  ChatModel chatModel,
                                  List<ToolCallback> toolCallbacks,
                                  StepTrackingToolInterceptor interceptor,
                                  boolean reasoningEnabled,
                                  com.alibaba.cloud.ai.graph.agent.hook.Hook... hooks) {
        var builder = ReactAgent.builder()
                .name(ASSISTANT_AGENT)
                .description(ASSISTANT_OWNER)
                .model(chatModel)
                .returnReasoningContents(reasoningEnabled)
                .systemPrompt(systemPrompt)
                .tools(toolCallbacks);

        if (hooks != null && hooks.length > 0) {
            builder.hooks(hooks);
        }
        if (interceptor != null) {
            builder.interceptors(interceptor);
        }

        return builder.build();
    }

    private <T> T invokeStructuredAgent(ChatModel chatModel,
                                        String systemPrompt,
                                        Object payload,
                                        TypeReference<T> typeReference) {
        try {
            Agent assistantAgent = buildReactAgent(systemPrompt, chatModel, List.of(), null, false);
            String input = objectMapper.writeValueAsString(payload);
            String response = assistantAgent.invoke(
                    List.of(new UserMessage(input)),
                    RunnableConfig.builder().threadId("assistant-structured").build()
            ).map(OverAllState::data)
                    .map(data -> data.get("messages"))
                    .flatMap(messages -> {
                        if (!(messages instanceof List<?> messageList) || messageList.isEmpty()) {
                            return Optional.<String>empty();
                        }
                        for (int index = messageList.size() - 1; index >= 0; index--) {
                            Object item = messageList.get(index);
                            if (item instanceof AssistantMessage assistantMessage && StringUtils.hasText(assistantMessage.getText())) {
                                return Optional.of(assistantMessage.getText());
                            }
                            if (item instanceof Message message && StringUtils.hasText(message.getText())) {
                                return Optional.of(message.getText());
                            }
                        }
                        return Optional.<String>empty();
                    })
                    .orElse("");

            return objectMapper.readValue(stripMarkdown(response), typeReference);
        } catch (Exception e) {
            throw new IllegalStateException("Assistant agent execution failed", e);
        }
    }

    private List<Message> loadConversationMessages(String conversationId, String userInput, ChatMemory chatMemory) {
        List<Message> messages = new ArrayList<>();
        if (chatMemory != null) {
            messages.addAll(chatMemory.get(conversationId));
        }

        if (messages.isEmpty()) {
            messages.add(new UserMessage(userInput));
            return messages;
        }

        Message lastMessage = messages.get(messages.size() - 1);
        if (!(lastMessage instanceof UserMessage userMessage) || !userInput.equals(userMessage.getText())) {
            messages.add(new UserMessage(userInput));
        }
        return messages;
    }

    private void handleNodeOutput(NodeOutput nodeOutput,
                                  StringBuilder responseBuilder,
                                  StringBuilder reasoningBuilder,
                                  boolean reasoningEnabled,
                                  ChatProcessStepTracker chatProcessStepTracker,
                                  Consumer<String> eventConsumer) {
        if (!(nodeOutput instanceof StreamingOutput<?> streamingOutput)) {
            return;
        }
        if (streamingOutput.getOutputType() != OutputType.AGENT_MODEL_STREAMING) {
            return;
        }

        String reasoningChunk = "";
        if (reasoningEnabled) {
            reasoningChunk = extractReasoningChunk(streamingOutput);
            if (StringUtils.hasText(reasoningChunk)) {
                reasoningBuilder.append(reasoningChunk);
                eventConsumer.accept(toJsonEvent("THINKING", Map.of("token", reasoningChunk)));
            }
        }

        String chunk = streamingOutput.chunk();
        if (!StringUtils.hasText(chunk) || chunk.equals(reasoningChunk)) {
            return;
        }

        if (chatProcessStepTracker != null) {
            chatProcessStepTracker.markAnswerStreamingStarted();
        }
        responseBuilder.append(chunk);
        eventConsumer.accept(toJsonEvent("ANSWER_DELTA", Map.of("token", chunk)));
    }

    private String extractReasoningChunk(StreamingOutput<?> streamingOutput) {
        String fromMessage = extractReasoningFromMessage(streamingOutput.message());
        if (StringUtils.hasText(fromMessage)) {
            return fromMessage;
        }

        Object originData = streamingOutput.getOriginData();
        if (originData instanceof OpenAiApi.ChatCompletionChunk chunk) {
            return chunk.choices().stream()
                    .map(choice -> choice.delta())
                    .filter(Objects::nonNull)
                    .map(OpenAiApi.ChatCompletionMessage::reasoningContent)
                    .filter(StringUtils::hasText)
                    .collect(java.util.stream.Collectors.joining());
        }
        return "";
    }

    private String extractReasoningFromMessage(Message message) {
        if (message == null) {
            return "";
        }
        return extractReasoningFromMetadata(message.getMetadata());
    }

    private String extractReasoningFromMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "";
        }

        for (String key : List.of("reasoningContent", "reasoning_content", "reasoning", "thinking")) {
            Object value = metadata.get(key);
            if (value instanceof String text && StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private Optional<String> extractFinalResponse(OverAllState state) {
        if (state == null) {
            return Optional.empty();
        }

        Object rawMessages = state.data().get("messages");
        if (!(rawMessages instanceof List<?> messageList) || messageList.isEmpty()) {
            return Optional.empty();
        }

        for (int index = messageList.size() - 1; index >= 0; index--) {
            Object item = messageList.get(index);
            if (item instanceof AssistantMessage assistantMessage && StringUtils.hasText(assistantMessage.getText())) {
                return Optional.of(assistantMessage.getText());
            }
            if (item instanceof Message message && StringUtils.hasText(message.getText())) {
                return Optional.of(message.getText());
            }
        }
        return Optional.empty();
    }

    private boolean isSaveMarkdownReportToolName(String toolName) {
        return "save_markdown_report".equals(normalizeToolName(toolName));
    }

    private String appendCapabilityHint(String prompt, Long connectionId, boolean noToolsAvailable) {
        if (connectionId == null) {
            return prompt + "\n" + PromptConstant.NO_CONNECTION_CAPABILITY_HINT;
        }
        if (noToolsAvailable) {
            return prompt + "\n" + PromptConstant.NO_TOOLS_CAPABILITY_HINT;
        }
        return prompt + "\n" + PromptConstant.TOOLS_READY_CAPABILITY_HINT;
    }

    private String normalizeToolName(String toolName) {
        return ToolNameNormalizer.canonicalize(toolName);
    }

    private List<Map<String, Object>> copySteps(List<Map<String, Object>> executedSteps) {
        synchronized (executedSteps) {
            List<Map<String, Object>> copied = new ArrayList<>(executedSteps.size());
            for (Map<String, Object> step : executedSteps) {
                copied.add(new LinkedHashMap<>(step));
            }
            return copied;
        }
    }

    private List<KnowledgeCitationDTO> copyCitations(List<KnowledgeCitationDTO> citations) {
        synchronized (citations) {
            return List.copyOf(citations);
        }
    }

    private void persistChatCompletionAsync(String workflowRunId,
                                            String setupStepId,
                                            String finalStepId,
                                            String conversationId,
                                            String inputSummary,
                                            String outputSummary,
                                            String fullResponse,
                                            List<Map<String, Object>> finalSteps,
                                            String reasoning,
                                            List<KnowledgeCitationDTO> finalCitations,
                                            ChatProcessStepTracker chatProcessStepTracker,
                                            Consumer<ChatExecutionResult> completionCallback) {
        CompletableFuture.runAsync(() -> {
            try {
                if (chatProcessStepTracker != null) {
                    chatProcessStepTracker.persistFinalizingStep(finalStepId, inputSummary, outputSummary);
                }
                workflowRunTracker.completeStep(
                        workflowRunId,
                        setupStepId,
                        "已进入回答整理阶段",
                        List.of("routeMode=" + ROUTE_MODE)
                );
                workflowRunTracker.addTimeline(workflowRunId, setupStepId, ASSISTANT_OWNER, "已进入回答生成阶段");
                workflowRunTracker.completeRun(workflowRunId, ROUTE_MODE);
            } catch (Exception e) {
                log.error("Failed to persist chat workflow completion. sessionId={}, workflowRunId={}",
                        conversationId, workflowRunId, e);
            }

            try {
                completionCallback.accept(new ChatExecutionResult(fullResponse, finalSteps, reasoning, finalCitations));
            } catch (Exception e) {
                log.error("Failed to persist chat history. sessionId={}, workflowRunId={}",
                        conversationId, workflowRunId, e);
            }
        });
    }

    private void persistChatFailureAsync(String workflowRunId,
                                         String setupStepId,
                                         String conversationId,
                                         String message,
                                         List<Map<String, Object>> finalSteps,
                                         List<KnowledgeCitationDTO> finalCitations,
                                         Consumer<ChatExecutionResult> completionCallback) {
        CompletableFuture.runAsync(() -> {
            try {
                workflowRunTracker.failStep(workflowRunId, setupStepId, "执行准备失败", List.of("routeMode=" + ROUTE_MODE));
                workflowRunTracker.failRun(workflowRunId, ROUTE_MODE, message);
            } catch (Exception e) {
                log.error("Failed to persist chat workflow failure. sessionId={}, workflowRunId={}",
                        conversationId, workflowRunId, e);
            }

            try {
                completionCallback.accept(new ChatExecutionResult(message, finalSteps, "", finalCitations));
            } catch (Exception e) {
                log.error("Failed to persist failed chat history. sessionId={}, workflowRunId={}",
                        conversationId, workflowRunId, e);
            }
        });
    }

    private String summarizeTitle(String text) {
        String normalized = summarizeText(text);
        return normalized.length() <= 24 ? normalized : normalized.substring(0, 24) + "...";
    }

    private String summarizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 96 ? normalized : normalized.substring(0, 96) + "...";
    }

    private String summarizeError(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current != null ? current.getMessage() : error.getMessage();
        return StringUtils.hasText(message) ? message : "unknown error";
    }

    private String toJsonEvent(String type, Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(Map.of("type", type, "data", data));
        } catch (Exception e) {
            log.error("Failed to serialize event", e);
            return "{}";
        }
    }

    private Optional<ToolExecutionRecord> findLatestSuccessfulToolExecution(List<ToolExecutionRecord> toolExecutions,
                                                                            String... toolNames) {
        List<String> normalizedToolNames = java.util.Arrays.stream(toolNames)
                .map(this::normalizeToolName)
                .toList();
        synchronized (toolExecutions) {
            for (int index = toolExecutions.size() - 1; index >= 0; index--) {
                ToolExecutionRecord record = toolExecutions.get(index);
                if (record.error() || !normalizedToolNames.contains(normalizeToolName(record.toolName()))) {
                    continue;
                }
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }

    private String resolveReportGenerationFailureMessage(String finalResponse) {
        if (StringUtils.hasText(finalResponse)) {
            return finalResponse.trim();
        }
        return "当前输入没有形成可执行的报表生成请求，请明确说明要生成图表还是文档报告。";
    }

    private Map<String, Object> parseRawToolResult(String rawResult) {
        if (!StringUtils.hasText(rawResult)) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(rawResult, MAP_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("报表工具返回结果解析失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractToolData(Map<String, Object> toolResponse) {
        Object data = toolResponse.get("data");
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castToMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String normalizeSql(String sql) {
        if (!StringUtils.hasText(sql)) {
            return "";
        }

        String normalized = sql.trim();
        if (normalized.startsWith("```sql")) {
            normalized = normalized.substring(6);
        }
        if (normalized.startsWith("```")) {
            normalized = normalized.substring(3);
        }
        if (normalized.endsWith("```")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        return normalized.trim();
    }

    private String stripMarkdown(String text) {
        if (text == null) {
            return "";
        }

        String normalized = text.trim();
        if (normalized.startsWith("```json")) {
            normalized = normalized.substring(7);
        } else if (normalized.startsWith("```")) {
            normalized = normalized.substring(3);
        }
        if (normalized.endsWith("```")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        return normalized.trim();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
