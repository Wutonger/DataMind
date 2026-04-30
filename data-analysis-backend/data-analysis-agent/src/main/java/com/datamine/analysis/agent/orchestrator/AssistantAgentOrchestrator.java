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
import com.datamine.analysis.agent.tool.*;
import com.datamine.analysis.agent.workflow.WorkflowRunTracker;
import com.datamine.analysis.common.dto.knowledge.KnowledgeCitationDTO;
import com.datamine.analysis.skills.tool.ToolStateKeys;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
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


    private static final String CHAT_AGENT_PROMPT = """
            你是 DataMind AI，负责围绕当前会话中的数据库、知识库和分析能力，给出准确、专业、直接的中文回答。

            请先理解用户真正要解决的问题，再自行决定是否需要使用工具或读取 skill。
            当结论依赖业务规则、指标口径、产品流程、专业文档、操作规范或引用依据时，优先查询知识库；
            当结论依赖真实表结构、字段、数据结果或 SQL 时，优先使用数据库相关能力核实；
            如果问题同时涉及知识库和数据库，先用知识库澄清定义和口径，再用数据库补充事实和结果。
            当需要更专业地组织洞察、风险、建议或结论时，可读取 insight-discovery skill 作为表达参考。
            如果需要编写 SQL，无论是自己回答、调用 db_execute，还是为图表/报告保存工具准备数据，都只能生成一条可直接执行的 SQL。
            不要返回多条以分号分隔的语句，不要先建临时表再查询；如果逻辑较复杂，请改写为单条 SQL，必要时使用子查询或 CTE。

            图表配置、分析结论和报告正文应由你自己完成生成；如果用户明确要求保存到报表中心，再调用对应的保存工具。
            不要向用户转述内部编排细节，也不要把不存在的工具名当作能力缺失原因直接说给用户。
            当前会话如果已经绑定数据库连接，默认就在这个上下文中工作，不要反复追问 connectionId。
            如果工具执行失败或当前缺少可用连接，只需清楚说明原因，并给出下一步建议。

            最终回答保持简洁、专业，不要暴露中间思考，不要输出 Thought、Action、Observation 或链路推理文本。
            """;

    private static final String SQL_AGENT_PROMPT = """
            你是 DataMind 的 SQL 助手，任务是结合当前连接下的数据库结构和必要的业务上下文，生成一条可以直接执行的 SQL。

            如果业务定义、指标口径、字段含义或报表规则可能影响 SQL 的正确性，先查询知识库，再结合 schema 相关能力确认表名和字段名。
            不要猜测不存在的表或字段；如果对数据库方言没有把握，优先选择更保守、更通用的写法。
            无论逻辑多复杂，都只能返回一条 SQL，不要输出多条以分号分隔的语句；如果需要分步处理，请改写成单条查询，必要时使用子查询或 CTE。
            除非用户明确要求全量数据，否则默认追加 LIMIT 100。

            最终只返回纯 SQL 本身，不要附加解释、注释、Markdown 或自然语言。返回内容必须可直接执行。
            """;


    private static final String REPORT_AGENT_PROMPT = """
            你是 DataMind 的报表生成助手，负责基于当前连接下的真实数据和必要的业务依据，产出适合保存到报表中心的图表报表或文档报告。

            先判断用户真正需要的是报表建议、图表、文档报告，还是普通问答。
            凡是涉及行业做法、业务规则、指标口径、报表设计建议或需要出处依据的内容，优先查询知识库；
            凡是需要真实字段、表关系或数据结果支撑时，先核实 schema，再在必要时执行 SQL。
            需要组织洞察、风险和建议时，可读取 insight-discovery skill 作为表达参考。
            如果需要 SQL 来支撑图表或报告，整个报表生成流程最多只允许一次 db_execute，且这次查询只能使用一条可执行 SQL；不要输出多条以分号分隔的语句，不要依赖临时表、变量或前置建表步骤。
            不要为了不同章节、不同统计项或不同分析段落分别执行多次查询；必须把整份报表所需数据尽量合并到一次查询中。
            如果逻辑复杂，请改写为单条查询，必要时使用子查询或 CTE；如果无法合并为单条 SQL，就明确说明当前需求不满足单 SQL 报表约束，不要继续保存报表。

            图表配置、报告正文和分析结论都应由你自己生成；工具只负责获取信息或保存结果，而不是替你写内容。
            只有当这唯一一次 SQL、对应查询结果和最终产物都已经准备好时，才调用对应的保存工具。
            如果用户的输入并不是一个明确的报表中心生成请求，不要勉强保存，直接提示用户补充想生成的内容即可。
            当前会话如果已经绑定数据库连接，默认就在这个上下文中工作，不要追问 connectionId。

            最终回答保持简洁、专业、中文输出，不要暴露中间思考、推理步骤或 Thought/Action/Observation。
            """;

    private static final String TABLE_DESCRIPTION_PROMPT = """
            你是 DataMind 的数据分析助手，负责为数据表生成简短业务描述。
            规则：
            1. 基于输入中的表名、注释和字段信息，给出每张表一句简短业务描述。
            2. 每条描述不超过 25 个中文字符，或一条非常短的中文句子。
            3. 返回 JSON，key 为 tableName，value 为描述。
            4. 不要输出任何额外解释。
            5. 当前会话如果已经绑定数据库连接，工具会自动使用当前连接，你不需要向用户追问 connectionId。
            """;

    private static final String GLOBAL_RELATION_PROMPT = """
            你是 DataMind 的数据分析助手，负责分析整个数据库的跨表关系。
            规则：
            1. 优先使用物理外键，并返回 type = fk。
            2. 可以根据 *_id 等候选字段推断逻辑关系，并返回 type = logical。
            3. 只返回指向真实存在表和字段的关系。
            4. 如果不够确定，就不要猜。
            5. 不要返回重复关系。
            6. 返回 JSON，使用源表名作为 key，value 为关系列表。
            7. 每个关系对象必须包含 column、targetTable、targetColumn、type。
            8. 不要输出任何额外解释。
            9. 当前会话如果已经绑定数据库连接，工具会自动使用当前连接，你不需要向用户追问 connectionId。
            """;

    private final ObjectMapper objectMapper;
    private final AgentToolCallbackFactory agentToolCallbackFactory;
    private final AgentSkillHookFactory agentSkillHookFactory;
    private final ToolProgressResolver toolProgressResolver;
    private final ToolResultProcessor toolResultProcessor;
    private final WorkflowRunTracker workflowRunTracker;

    /**
     * 处理聊天场景的流式输出，并在结束时返回完整执行结果。
     */
    public Flux<String> orchestrateStream(String sessionId,
                                          Long connectionId,
                                          String userInput,
                                          ChatClient chatClient,
                                          ChatMemory chatMemory,
                                          Consumer<ChatExecutionResult> completionCallback) {
        String conversationId = StringUtils.hasText(sessionId) ? sessionId : String.valueOf(connectionId);

        return Flux.create(emitter -> {
            List<Map<String, Object>> executedSteps = java.util.Collections.synchronizedList(new ArrayList<>());
            List<KnowledgeCitationDTO> collectedCitations = java.util.Collections.synchronizedList(new ArrayList<>());
            AtomicReference<OverAllState> latestState = new AtomicReference<>();
            StringBuilder responseBuilder = new StringBuilder();
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
                        "title", summarizeTitle(userInput)
                )));

                List<Message> inputMessages = loadConversationMessages(conversationId, userInput, chatMemory);
                Agent assistantAgent = buildChatAgent(
                        connectionId,
                        userInput,
                        chatClient,
                        chatMemory,
                        eventConsumer,
                        executedSteps,
                        collectedCitations,
                        workflowRunId
                );

                RunnableConfig runnableConfig = RunnableConfig.builder()
                        .threadId(conversationId)
                        .addStateUpdate(Map.of(
                                ToolStateKeys.CURRENT_WORKFLOW_RUN_ID, workflowRunId
                        ))
                        .build();

                log.info("Starting chat workflow. sessionId={}, connectionId={}", conversationId, connectionId);

                Disposable disposable = assistantAgent.stream(inputMessages, runnableConfig)
                        .doOnNext(nodeOutput -> latestState.set(nodeOutput.state()))
                        .subscribe(nodeOutput -> handleNodeOutput(nodeOutput, responseBuilder, eventConsumer),
                                error -> {
                                    String message = summarizeError(error);
                                    List<Map<String, Object>> finalSteps = copySteps(executedSteps);
                                    List<KnowledgeCitationDTO> finalCitations = copyCitations(collectedCitations);
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
                                    String fullResponse = extractFinalResponse(latestState.get())
                                            .filter(StringUtils::hasText)
                                            .orElseGet(responseBuilder::toString);

                                    if (!StringUtils.hasText(fullResponse)) {
                                        fullResponse = "抱歉，我暂时没有生成有效回复。";
                                    }

                                    List<KnowledgeCitationDTO> finalCitations = copyCitations(collectedCitations);
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
                                            userInput,
                                            fullResponse,
                                            finalSteps,
                                            finalCitations,
                                            completionCallback
                                    );
                                });
                emitter.onDispose(disposable);
            } catch (Exception error) {
                String message = summarizeError(error);
                List<Map<String, Object>> finalSteps = copySteps(executedSteps);
                List<KnowledgeCitationDTO> finalCitations = copyCitations(collectedCitations);
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
                                          ChatClient chatClient) {
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

            Agent assistantAgent = buildSqlAgent(connectionId, userInput, chatClient, executedSteps, workflowRunId);

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
                                                ChatClient chatClient) {
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

            Agent assistantAgent = buildReportAgent(connectionId, userRequirement, chatClient, executedSteps, toolExecutions::add, workflowRunId);

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
    public Map<String, String> generateTableDescriptions(ChatClient chatClient,
                                                         List<Map<String, Object>> tablePayload) {
        return invokeStructuredAgent(chatClient, TABLE_DESCRIPTION_PROMPT, tablePayload, new TypeReference<>() {
        });
    }

    /**
     * 根据全库结构信息输出标准化关系结果。
     */
    public Map<String, List<Map<String, Object>>> analyzeGlobalRelations(ChatClient chatClient,
                                                                         Map<String, Object> relationPayload) {
        return invokeStructuredAgent(chatClient, GLOBAL_RELATION_PROMPT, relationPayload, new TypeReference<>() {
        });
    }

    private Agent buildChatAgent(Long connectionId,
                                 String userInput,
                                 ChatClient chatClient,
                                 ChatMemory chatMemory,
                                 Consumer<String> eventConsumer,
                                 List<Map<String, Object>> executedSteps,
                                 List<KnowledgeCitationDTO> collectedCitations,
                                 String workflowRunId) {
        List<ToolCallback> allCallbacks = agentToolCallbackFactory.buildCallbacks(connectionId, userInput, chatClient);
        List<ToolCallback> baseCallbacks = agentSkillHookFactory.resolveBaseTools(allCallbacks);
        StepTrackingToolInterceptor interceptor = buildTrackingInterceptor(
                eventConsumer,
                executedSteps,
                collectedCitations,
                allCallbacks,
                this::toJsonEvent,
                null,
                workflowRunId
        );

        return buildReactAgent(
                appendCapabilityHint(CHAT_AGENT_PROMPT, connectionId, allCallbacks.isEmpty()),
                chatClient,
                baseCallbacks,
                interceptor,
                agentSkillHookFactory.buildHook(allCallbacks)
        );
    }

    private Agent buildSqlAgent(Long connectionId,
                                String userInput,
                                ChatClient chatClient,
                                List<Map<String, Object>> executedSteps,
                                String workflowRunId) {
        List<ToolCallback> allCallbacks = agentToolCallbackFactory.buildCallbacks(connectionId, userInput, chatClient);
        List<ToolCallback> sqlCallbacks = filterSqlCallbacks(allCallbacks);
        List<ToolCallback> baseCallbacks = agentSkillHookFactory.resolveBaseTools(sqlCallbacks);

        if (sqlCallbacks.stream().noneMatch(callback -> isSchemaToolName(callback.getToolDefinition().name()))) {
            throw new IllegalStateException("No schema tools are available for the current connection. Please confirm MCP services are running.");
        }

        StepTrackingToolInterceptor interceptor = buildTrackingInterceptor(
                event -> {
                },
                executedSteps,
                new ArrayList<>(),
                sqlCallbacks,
                (type, data) -> "",
                null,
                workflowRunId
        );

        return buildReactAgent(SQL_AGENT_PROMPT, chatClient, baseCallbacks, interceptor, agentSkillHookFactory.buildHook(sqlCallbacks));
    }

    private Agent buildReportAgent(Long connectionId,
                                   String userInput,
                                   ChatClient chatClient,
                                   List<Map<String, Object>> executedSteps,
                                   Consumer<ToolExecutionRecord> toolResultConsumer,
                                   String workflowRunId) {
        List<ToolCallback> allCallbacks = agentToolCallbackFactory.buildCallbacks(connectionId, userInput, chatClient);
        List<ToolCallback> reportCallbacks = filterReportCallbacks(allCallbacks);
        List<ToolCallback> baseCallbacks = agentSkillHookFactory.resolveBaseTools(reportCallbacks);

        if (reportCallbacks.stream().noneMatch(callback -> isReportArtifactToolName(callback.getToolDefinition().name()))) {
            throw new IllegalStateException("No report artifact tools are available. Please confirm the related skills are registered.");
        }

        StepTrackingToolInterceptor interceptor = buildTrackingInterceptor(
                event -> {
                },
                executedSteps,
                new ArrayList<>(),
                reportCallbacks,
                (type, data) -> "",
                toolResultConsumer,
                workflowRunId
        );

        return buildReactAgent(REPORT_AGENT_PROMPT, chatClient, baseCallbacks, interceptor, agentSkillHookFactory.buildHook(reportCallbacks));
    }

    private StepTrackingToolInterceptor buildTrackingInterceptor(Consumer<String> eventConsumer,
                                                                 List<Map<String, Object>> executedSteps,
                                                                 List<KnowledgeCitationDTO> collectedCitations,
                                                                 List<ToolCallback> toolCallbacks,
                                                                 BiFunction<String, Map<String, Object>, String> eventJsonBuilder,
                                                                 Consumer<ToolExecutionRecord> toolResultConsumer,
                                                                 String workflowRunId) {
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
                workflowRunId
        );
    }

    private Agent buildReactAgent(String systemPrompt,
                                  ChatClient chatClient,
                                  List<ToolCallback> toolCallbacks,
                                  StepTrackingToolInterceptor interceptor,
                                  com.alibaba.cloud.ai.graph.agent.hook.Hook... hooks) {
        var builder = ReactAgent.builder()
                .name(ASSISTANT_AGENT)
                .description(ASSISTANT_OWNER)
                .chatClient(chatClient)
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

    private <T> T invokeStructuredAgent(ChatClient chatClient,
                                        String systemPrompt,
                                        Object payload,
                                        TypeReference<T> typeReference) {
        try {
            Agent assistantAgent = buildReactAgent(systemPrompt, chatClient, List.of(), null);
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
                                  Consumer<String> eventConsumer) {
        if (!(nodeOutput instanceof StreamingOutput<?> streamingOutput)) {
            return;
        }
        if (streamingOutput.getOutputType() != OutputType.AGENT_MODEL_STREAMING) {
            return;
        }

        String chunk = streamingOutput.chunk();
        if (!StringUtils.hasText(chunk)) {
            return;
        }

        responseBuilder.append(chunk);
        eventConsumer.accept(toJsonEvent("THINKING", Map.of("token", chunk)));
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

    private List<ToolCallback> filterSqlCallbacks(List<ToolCallback> callbacks) {
        return callbacks.stream()
                .filter(callback -> {
                    String toolName = callback.getToolDefinition().name();
                    return isKnowledgeToolName(toolName) || isSchemaToolName(toolName);
                })
                .toList();
    }

    private List<ToolCallback> filterReportCallbacks(List<ToolCallback> callbacks) {
        return callbacks.stream()
                .filter(callback -> {
                    String toolName = callback.getToolDefinition().name();
                    return isKnowledgeToolName(toolName)
                            || isSchemaToolName(toolName)
                            || isDbExecuteToolName(toolName)
                            || isReportArtifactToolName(toolName);
                })
                .toList();
    }

    private boolean isKnowledgeToolName(String toolName) {
        return "knowledge_search".equals(normalizeToolName(toolName));
    }

    private boolean isSchemaToolName(String toolName) {
        String normalized = normalizeToolName(toolName);
        return "db_get_schema".equals(normalized)
                || "db_list_tables".equals(normalized)
                || "db_get_columns".equals(normalized);
    }

    private boolean isDbExecuteToolName(String toolName) {
        return "db_execute".equals(normalizeToolName(toolName));
    }

    private boolean isSaveChartReportToolName(String toolName) {
        return "save_chart_report".equals(normalizeToolName(toolName));
    }

    private boolean isSaveMarkdownReportToolName(String toolName) {
        return "save_markdown_report".equals(normalizeToolName(toolName));
    }

    private boolean isReportArtifactToolName(String toolName) {
        return isSaveChartReportToolName(toolName) || isSaveMarkdownReportToolName(toolName);
    }

    private String appendCapabilityHint(String prompt, Long connectionId, boolean noToolsAvailable) {
        if (connectionId == null) {
            return prompt + "\n当前没有绑定连接，不能编造数据库或知识库内容。";
        }
        if (noToolsAvailable) {
            return prompt + "\n当前连接下没有可用工具，不能编造数据库或知识库内容。";
        }
        return prompt + "\n当前连接下的可用工具已经加载，你可以按需自主选择调用。";
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
                                            String userInput,
                                            String fullResponse,
                                            List<Map<String, Object>> finalSteps,
                                            List<KnowledgeCitationDTO> finalCitations,
                                            Consumer<ChatExecutionResult> completionCallback) {
        CompletableFuture.runAsync(() -> {
            try {
                workflowRunTracker.completeStep(
                        workflowRunId,
                        setupStepId,
                        "已进入回答整理阶段",
                        List.of("routeMode=" + ROUTE_MODE)
                );
                workflowRunTracker.addTimeline(workflowRunId, setupStepId, ASSISTANT_OWNER, "已进入回答生成阶段");
                workflowRunTracker.startStep(
                        workflowRunId,
                        finalStepId,
                        ASSISTANT_AGENT,
                        ASSISTANT_OWNER,
                        "整理最终回答",
                        "Final Synthesis",
                        summarizeText(userInput),
                        List.of("final_response")
                );
                workflowRunTracker.addTimeline(workflowRunId, finalStepId, ASSISTANT_OWNER, "开始整理最终回答");
                workflowRunTracker.completeStep(
                        workflowRunId,
                        finalStepId,
                        summarizeText(fullResponse),
                        List.of("final_response")
                );
                workflowRunTracker.addTimeline(workflowRunId, finalStepId, ASSISTANT_OWNER, "最终回答已生成");
                workflowRunTracker.completeRun(workflowRunId, ROUTE_MODE);
            } catch (Exception e) {
                log.error("Failed to persist chat workflow completion. sessionId={}, workflowRunId={}",
                        conversationId, workflowRunId, e);
            }

            try {
                completionCallback.accept(new ChatExecutionResult(fullResponse, finalSteps, finalCitations));
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
                completionCallback.accept(new ChatExecutionResult(message, finalSteps, finalCitations));
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
