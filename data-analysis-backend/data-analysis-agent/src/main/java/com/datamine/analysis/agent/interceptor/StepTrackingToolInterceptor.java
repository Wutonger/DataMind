package com.datamine.analysis.agent.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import com.datamine.analysis.agent.model.ToolExecutionRecord;
import com.datamine.analysis.agent.orchestrator.AssistantAgentOrchestrator;
import com.datamine.analysis.agent.tool.ToolProgressDescriptor;
import com.datamine.analysis.agent.tool.ToolProgressResolver;
import com.datamine.analysis.agent.workflow.WorkflowRunTracker;
import com.datamine.analysis.agent.tool.ToolResultProcessor;
import com.datamine.analysis.common.dto.knowledge.KnowledgeCitationDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * 负责拦截工具调用，并把工具执行过程转换成可展示的步骤事件。
 */
public final class StepTrackingToolInterceptor extends ToolInterceptor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final Consumer<String> eventConsumer;
    private final List<Map<String, Object>> executedSteps;
    private final List<KnowledgeCitationDTO> collectedCitations;
    private final Map<String, ToolProgressDescriptor> toolProgressDescriptors;
    private final ToolProgressResolver toolProgressResolver;
    private final ToolResultProcessor toolResultProcessor;
    private final BiFunction<String, Map<String, Object>, String> eventJsonBuilder;
    private final Consumer<ToolExecutionRecord> toolResultConsumer;
    private final WorkflowRunTracker workflowRunTracker;
    private final String workflowRunId;

    public StepTrackingToolInterceptor(Consumer<String> eventConsumer,
                                       List<Map<String, Object>> executedSteps,
                                       List<KnowledgeCitationDTO> collectedCitations,
                                       Map<String, ToolProgressDescriptor> toolProgressDescriptors,
                                       ToolProgressResolver toolProgressResolver,
                                       ToolResultProcessor toolResultProcessor,
                                       BiFunction<String, Map<String, Object>, String> eventJsonBuilder,
                                       Consumer<ToolExecutionRecord> toolResultConsumer,
                                       WorkflowRunTracker workflowRunTracker,
                                       String workflowRunId) {
        this.eventConsumer = eventConsumer;
        this.executedSteps = executedSteps;
        this.collectedCitations = collectedCitations;
        this.toolProgressDescriptors = toolProgressDescriptors;
        this.toolProgressResolver = toolProgressResolver;
        this.toolResultProcessor = toolResultProcessor;
        this.eventJsonBuilder = eventJsonBuilder;
        this.toolResultConsumer = toolResultConsumer;
        this.workflowRunTracker = workflowRunTracker;
        this.workflowRunId = workflowRunId;
    }

    @Override
    public String getName() {
        return "datamind-step-tracking";
    }

    /**
     * 拦截每一次工具调用，把开始、完成、失败都转换成统一步骤事件。
     * 同时在成功时抽取知识库引用，供最终回答一起返回。
     */
    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        String stepId = StringUtils.hasText(request.getToolCallId())
                ? request.getToolCallId()
                : UUID.randomUUID().toString();
        String toolName = request.getToolName();
        ToolProgressDescriptor descriptor = resolveDescriptor(request);
        String displayName = descriptor.displayName();
        String description = descriptor.description();
        String agentId = resolveAgentId(toolName);
        String owner = resolveOwner(agentId);

        // 前端进度面板直接依赖这份 step 结构，字段保持稳定，便于后续合并与回放。
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", stepId);
        step.put("name", displayName);
        step.put("skill", resolveStepSkillName(toolName, request.getArguments()));
        step.put("displayName", displayName);
        step.put("description", description);
        step.put("status", "RUNNING");
        synchronized (executedSteps) {
            executedSteps.add(step);
        }

        workflowRunTracker.startStep(
                workflowRunId,
                stepId,
                agentId,
                owner,
                displayName,
                "Tool Call",
                description,
                List.of(toolName)
        );
        workflowRunTracker.addTimeline(workflowRunId, stepId, owner, "开始执行 " + displayName);

        eventConsumer.accept(eventJsonBuilder.apply("STEP_STARTED", Map.of(
                "stepId", stepId,
                "stepName", resolveEventStepName(toolName, request.getArguments(), displayName),
                "displayName", displayName,
                "description", description
        )));

        try {
            ToolCallResponse response = handler.call(request);
            publishToolResult(stepId, toolName, response.getResult(), response.isError());
            String result = toolResultProcessor.summarize(response.getResult());
            step.put("status", response.isError() ? "FAILED" : "COMPLETED");
            step.put("result", result);

            if (!response.isError()) {
                toolResultProcessor.mergeKnowledgeCitations(toolName, response.getResult(), collectedCitations);
            }

            if (response.isError()) {
                workflowRunTracker.failStep(workflowRunId, stepId, result, List.of(toolName));
                workflowRunTracker.addTimeline(workflowRunId, stepId, owner, displayName + " 执行失败");
                eventConsumer.accept(eventJsonBuilder.apply("STEP_FAILED", Map.of(
                        "stepId", stepId,
                        "error", result
                )));
            } else {
                workflowRunTracker.completeStep(workflowRunId, stepId, result, List.of(toolName));
                workflowRunTracker.addTimeline(workflowRunId, stepId, owner, displayName + " 执行完成");
                eventConsumer.accept(eventJsonBuilder.apply("STEP_COMPLETED", Map.of(
                        "stepId", stepId,
                        "result", result
                )));
            }

            return response;
        } catch (RuntimeException e) {
            String errorMessage = summarizeError(e);
            publishToolResult(stepId, toolName, errorMessage, true);
            step.put("status", "FAILED");
            step.put("result", errorMessage);
            workflowRunTracker.failStep(workflowRunId, stepId, errorMessage, List.of(toolName));
            workflowRunTracker.addTimeline(workflowRunId, stepId, owner, displayName + " 执行失败");
            eventConsumer.accept(eventJsonBuilder.apply("STEP_FAILED", Map.of("stepId", stepId, "error", errorMessage)));
            throw e;
        }
    }

    /**
     * SkillsAgentHook 提供的 read_skill 不在业务 Tool 列表中，这里补一层专门映射，
     * 让前端步骤能看到具体读取的是哪个 skill。
     */
    private ToolProgressDescriptor resolveDescriptor(ToolCallRequest request) {
        String toolName = request.getToolName();
        if ("read_skill".equalsIgnoreCase(toolName)) {
            return resolveReadSkillDescriptor(request.getArguments());
        }
        return toolProgressDescriptors.getOrDefault(
                toolName,
                toolProgressResolver.resolveByName(toolName));
    }

    private ToolProgressDescriptor resolveReadSkillDescriptor(String arguments) {
        String skillName = extractSkillName(arguments);
        String friendlySkillName = resolveFriendlySkillName(skillName);
        return new ToolProgressDescriptor(
                "调用" + friendlySkillName,
                "加载" + friendlySkillName + "并准备后续处理"
        );
    }

    private String resolveStepSkillName(String toolName, String arguments) {
        if (!"read_skill".equalsIgnoreCase(toolName)) {
            return toolName;
        }
        return resolveFriendlySkillName(extractSkillName(arguments));
    }

    private String resolveEventStepName(String toolName, String arguments, String displayName) {
        if (!"read_skill".equalsIgnoreCase(toolName)) {
            return toolName;
        }
        return StringUtils.hasText(displayName) ? displayName : "调用技能";
    }

    private String extractSkillName(String arguments) {
        if (!StringUtils.hasText(arguments)) {
            return "";
        }
        try {
            Map<String, Object> payload = OBJECT_MAPPER.readValue(arguments, MAP_TYPE);
            Object skillName = payload.get("skillName");
            if (skillName == null) {
                skillName = payload.get("skill_name");
            }
            if (skillName == null) {
                skillName = payload.get("name");
            }
            return skillName == null ? "" : String.valueOf(skillName).trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String resolveFriendlySkillName(String skillName) {
        if (!StringUtils.hasText(skillName)) {
            return "技能";
        }
        return switch (skillName.trim().toLowerCase()) {
            case "knowledge-grounding" -> "知识库技能";
            case "artifact-generation" -> "图表报告技能";
            case "insight-discovery", "insight_discovery" -> "洞察分析技能";
            case "visualization" -> "图表生成技能";
            case "report-center", "report_center" -> "文档报告技能";
            default -> "技能";
        };
    }

    private String resolveAgentId(String toolName) {
        return AssistantAgentOrchestrator.ASSISTANT_AGENT;
    }

    private String resolveOwner(String agentId) {
        return AssistantAgentOrchestrator.ASSISTANT_OWNER;
    }

    /**
     * 工具失败时同样只向外暴露收敛后的短错误，避免底层包装异常直接透出。
     */
    private String summarizeError(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current != null ? current.getMessage() : error.getMessage();
        return StringUtils.hasText(message) ? message : "Chat request failed";
    }

    private void publishToolResult(String stepId, String toolName, String rawResult, boolean error) {
        if (toolResultConsumer == null) {
            return;
        }
        toolResultConsumer.accept(new ToolExecutionRecord(stepId, toolName, rawResult, error));
    }
}
