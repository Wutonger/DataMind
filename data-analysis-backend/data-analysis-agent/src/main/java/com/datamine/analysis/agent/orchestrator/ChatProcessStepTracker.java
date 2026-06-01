package com.datamine.analysis.agent.orchestrator;

import com.datamine.analysis.agent.workflow.WorkflowRunTracker;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * 统一管理聊天过程中的结构化步骤。
 * thinking / finalizing 由这里负责，tool / skill 步骤仍由拦截器负责。
 */
public final class ChatProcessStepTracker {

    private static final String STEP_KIND_THINKING = "thinking";
    private static final String STEP_KIND_FINALIZING = "finalizing";

    private static final String THINKING_TITLE = "正在思考";
    private static final String THINKING_DESCRIPTION = "分析当前问题与上下文，决定下一步操作";
    private static final String FOLLOW_UP_THINKING_DESCRIPTION = "结合最新结果，继续判断下一步操作";
    private static final String FINALIZING_TITLE = "整理最终回答";
    private static final String FINALIZING_DESCRIPTION = "整合已有结果，输出最终回答";

    private final String workflowRunId;
    private final String agentId;
    private final String owner;
    private final WorkflowRunTracker workflowRunTracker;
    private final List<Map<String, Object>> executedSteps;
    private final Consumer<String> eventConsumer;
    private final BiFunction<String, Map<String, Object>, String> eventJsonBuilder;

    private int thinkingSequence = 0;
    private String activeThinkingStepId;
    private boolean answerStreamingStarted = false;
    private boolean finalizingRecorded = false;

    public ChatProcessStepTracker(String workflowRunId,
                                  String agentId,
                                  String owner,
                                  WorkflowRunTracker workflowRunTracker,
                                  List<Map<String, Object>> executedSteps,
                                  Consumer<String> eventConsumer,
                                  BiFunction<String, Map<String, Object>, String> eventJsonBuilder) {
        this.workflowRunId = workflowRunId;
        this.agentId = agentId;
        this.owner = owner;
        this.workflowRunTracker = workflowRunTracker;
        this.executedSteps = executedSteps;
        this.eventConsumer = eventConsumer;
        this.eventJsonBuilder = eventJsonBuilder;
    }

    /**
     * 模型开始规划当前问题时，创建第一条“正在思考”步骤。
     */
    public synchronized void startInitialThinking() {
        startThinking(THINKING_DESCRIPTION, "开始分析用户问题");
    }

    /**
     * 工具执行前，结束当前 thinking，表示模型已经决定好下一步动作。
     */
    public synchronized void beforeToolExecution() {
        completeActiveThinking("已确定下一步操作");
    }

    /**
     * 工具执行后，如果还没有开始正式回答，则补一条新的 thinking。
     */
    public synchronized void afterToolExecution() {
        if (answerStreamingStarted || finalizingRecorded) {
            return;
        }
        startThinking(FOLLOW_UP_THINKING_DESCRIPTION, "基于最新结果继续思考");
    }

    /**
     * 一旦开始输出最终回答，就不再保留“正在思考”的运行态。
     */
    public synchronized void markAnswerStreamingStarted() {
        if (answerStreamingStarted) {
            return;
        }
        answerStreamingStarted = true;
        completeActiveThinking("已开始输出回答");
    }

    /**
     * 先快速补齐前端可见的“整理最终回答”步骤，不阻塞 SSE 收尾。
     */
    public synchronized void recordFinalizingStep(String stepId, String outputSummary) {
        if (finalizingRecorded) {
            return;
        }

        markAnswerStreamingStarted();
        finalizingRecorded = true;

        Map<String, Object> step = createStep(
                stepId,
                FINALIZING_TITLE,
                FINALIZING_DESCRIPTION,
                STEP_KIND_FINALIZING,
                "COMPLETED"
        );
        if (StringUtils.hasText(outputSummary)) {
            step.put("result", outputSummary);
        }
        addStep(step);

        emitStepStarted(stepId, FINALIZING_TITLE, FINALIZING_DESCRIPTION, STEP_KIND_FINALIZING);
        emitStepCompleted(stepId, outputSummary, STEP_KIND_FINALIZING);
    }

    /**
     * 在流结束后异步补齐 workflow 持久化，避免最后一段回答结束后仍等待写库。
     */
    public synchronized void persistFinalizingStep(String stepId, String inputSummary, String outputSummary) {
        if (!finalizingRecorded) {
            return;
        }

        workflowRunTracker.startStep(
                workflowRunId,
                stepId,
                agentId,
                owner,
                FINALIZING_TITLE,
                "Final Synthesis",
                inputSummary,
                List.of("final_response")
        );
        workflowRunTracker.addTimeline(workflowRunId, stepId, owner, "开始整理最终回答");
        workflowRunTracker.completeStep(workflowRunId, stepId, outputSummary, List.of("final_response"));
        workflowRunTracker.addTimeline(workflowRunId, stepId, owner, "最终回答已生成");
    }

    /**
     * 异常结束时，把当前未结束的 thinking 标记为失败。
     */
    public synchronized void failActiveStep(String errorMessage) {
        if (!StringUtils.hasText(activeThinkingStepId)) {
            return;
        }

        updateStep(activeThinkingStepId, "FAILED", errorMessage);
        workflowRunTracker.failStep(workflowRunId, activeThinkingStepId, errorMessage, List.of());
        workflowRunTracker.addTimeline(workflowRunId, activeThinkingStepId, owner, THINKING_TITLE + "中断");
        emitStepFailed(activeThinkingStepId, errorMessage, STEP_KIND_THINKING);
        activeThinkingStepId = null;
    }

    private void startThinking(String description, String timelineMessage) {
        if (StringUtils.hasText(activeThinkingStepId) || answerStreamingStarted || finalizingRecorded) {
            return;
        }

        String stepId = workflowRunId + "-thinking-" + (++thinkingSequence);
        activeThinkingStepId = stepId;

        addStep(createStep(stepId, THINKING_TITLE, description, STEP_KIND_THINKING, "RUNNING"));
        workflowRunTracker.startStep(
                workflowRunId,
                stepId,
                agentId,
                owner,
                THINKING_TITLE,
                "Model Thinking",
                description,
                List.of()
        );
        workflowRunTracker.addTimeline(workflowRunId, stepId, owner, timelineMessage);
        emitStepStarted(stepId, THINKING_TITLE, description, STEP_KIND_THINKING);
    }

    private void completeActiveThinking(String result) {
        if (!StringUtils.hasText(activeThinkingStepId)) {
            return;
        }

        String stepId = activeThinkingStepId;
        updateStep(stepId, "COMPLETED", result);
        workflowRunTracker.completeStep(workflowRunId, stepId, result, List.of());
        workflowRunTracker.addTimeline(workflowRunId, stepId, owner, THINKING_TITLE + "完成");
        emitStepCompleted(stepId, result, STEP_KIND_THINKING);
        activeThinkingStepId = null;
    }

    private void addStep(Map<String, Object> step) {
        synchronized (executedSteps) {
            executedSteps.add(step);
        }
    }

    private void updateStep(String stepId, String status, String result) {
        synchronized (executedSteps) {
            for (Map<String, Object> step : executedSteps) {
                if (stepId.equals(step.get("id"))) {
                    step.put("status", status);
                    if (StringUtils.hasText(result)) {
                        step.put("result", result);
                    }
                    return;
                }
            }
        }
    }

    private Map<String, Object> createStep(String stepId,
                                           String title,
                                           String description,
                                           String kind,
                                           String status) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", stepId);
        step.put("name", title);
        step.put("skill", title);
        step.put("displayName", title);
        step.put("description", description);
        step.put("kind", kind);
        step.put("status", status);
        return step;
    }

    private void emitStepStarted(String stepId, String stepName, String description, String stepKind) {
        eventConsumer.accept(eventJsonBuilder.apply("STEP_STARTED", Map.of(
                "stepId", stepId,
                "stepName", stepName,
                "displayName", stepName,
                "description", description,
                "stepKind", stepKind
        )));
    }

    private void emitStepCompleted(String stepId, String result, String stepKind) {
        eventConsumer.accept(eventJsonBuilder.apply("STEP_COMPLETED", Map.of(
                "stepId", stepId,
                "result", StringUtils.hasText(result) ? result : "",
                "stepKind", stepKind
        )));
    }

    private void emitStepFailed(String stepId, String error, String stepKind) {
        eventConsumer.accept(eventJsonBuilder.apply("STEP_FAILED", Map.of(
                "stepId", stepId,
                "error", StringUtils.hasText(error) ? error : "处理失败",
                "stepKind", stepKind
        )));
    }
}
