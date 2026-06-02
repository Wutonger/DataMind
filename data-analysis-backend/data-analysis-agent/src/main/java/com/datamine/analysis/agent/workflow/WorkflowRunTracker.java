package com.datamine.analysis.agent.workflow;

import com.datamine.analysis.common.dto.workflow.WorkflowRunDTO;
import com.datamine.analysis.common.dto.workflow.WorkflowStepDTO;
import com.datamine.analysis.common.dto.workflow.WorkflowTimelineItemDTO;
import com.datamine.analysis.common.entity.WorkflowRunEntity;
import com.datamine.analysis.common.entity.WorkflowStepEntity;
import com.datamine.analysis.common.entity.WorkflowTimelineEntity;
import com.datamine.analysis.common.repository.SysUserRepository;
import com.datamine.analysis.common.repository.WorkflowRunRepository;
import com.datamine.analysis.common.repository.WorkflowStepRepository;
import com.datamine.analysis.common.repository.WorkflowTimelineRepository;
import com.datamine.analysis.common.util.SnowflakeIdGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowRunTracker {

    private static final int MAX_ACTIVE_RUNS = 120;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final WorkflowRunRepository workflowRunRepository;
    private final WorkflowStepRepository workflowStepRepository;
    private final WorkflowTimelineRepository workflowTimelineRepository;
    private final SysUserRepository sysUserRepository;
    private final ObjectMapper objectMapper;

    private final Map<String, MutableWorkflowRun> activeRuns = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> runOrder = new ConcurrentLinkedDeque<>();

    @Transactional
    public String startRun(String scene, String title) {
        return startRun(scene, null, null, title);
    }

    @Transactional
    public String startRun(String scene, Long connectionId, String title) {
        return startRun(scene, null, connectionId, title);
    }

    @Transactional
    public String startRun(String scene, Long userId, Long connectionId, String title) {
        String runId = "wf_" + snowflakeIdGenerator.nextId();
        MutableWorkflowRun run = new MutableWorkflowRun(runId, normalizeScene(scene), userId, connectionId, normalizeTitle(title));
        activeRuns.put(runId, run);
        runOrder.addFirst(runId);
        trimActiveRuns();
        persistRun(run);
        return runId;
    }

    @Transactional
    public void setRouteMode(String runId, String routeMode) {
        mutateRun(runId, run -> {
            run.routeMode = normalizeRouteMode(routeMode);
            persistRun(run);
        });
    }

    @Transactional
    public void startStep(String runId,
                          String stepId,
                          String agentId,
                          String owner,
                          String title,
                          String kind,
                          String inputSummary,
                          List<String> tools) {
        mutateRun(runId, run -> {
            boolean isNewStep = !run.steps.containsKey(stepId);
            MutableWorkflowStep step = run.steps.computeIfAbsent(stepId, key -> new MutableWorkflowStep());
            step.id = stepId;
            step.order = isNewStep ? run.stepOrder.size() + 1 : step.order;
            step.agentId = normalizeAgentId(agentId);
            step.owner = StringUtils.hasText(owner) ? owner.trim() : resolveOwner(step.agentId);
            step.title = defaultIfBlank(title, "处理步骤");
            step.kind = defaultIfBlank(kind, "Workflow Step");
            step.status = "RUNNING";
            step.inputSummary = defaultIfBlank(inputSummary, "等待输入");
            step.outputSummary = defaultIfBlank(step.outputSummary, "");
            step.tools = normalizeTools(tools);
            step.startedAt = step.startedAt == null ? LocalDateTime.now() : step.startedAt;
            step.finishedAt = null;

            if (isNewStep) {
                run.stepOrder.add(stepId);
            }

            appendPath(run, step.agentId, step.owner);
            persistRun(run);
            persistStep(run.id, step);
        });
    }

    @Transactional
    public void completeStep(String runId, String stepId, String outputSummary, List<String> tools) {
        mutateRun(runId, run -> {
            MutableWorkflowStep step = run.steps.get(stepId);
            if (step == null) {
                return;
            }
            step.status = "COMPLETED";
            step.outputSummary = defaultIfBlank(outputSummary, "步骤执行完成");
            step.tools = mergeTools(step.tools, tools);
            step.finishedAt = LocalDateTime.now();
            persistStep(run.id, step);
        });
    }

    @Transactional
    public void failStep(String runId, String stepId, String outputSummary, List<String> tools) {
        mutateRun(runId, run -> {
            MutableWorkflowStep step = run.steps.get(stepId);
            if (step == null) {
                return;
            }
            step.status = "FAILED";
            step.outputSummary = defaultIfBlank(outputSummary, "步骤执行失败");
            step.tools = mergeTools(step.tools, tools);
            step.finishedAt = LocalDateTime.now();
            persistStep(run.id, step);
        });
    }

    @Transactional
    public void addTimeline(String runId, String nodeId, String title, String message) {
        mutateRun(runId, run -> appendTimeline(run, nodeId, title, message));
    }

    @Transactional
    public void completeRun(String runId, String routeMode) {
        mutateRun(runId, run -> {
            run.routeMode = normalizeRouteMode(routeMode);
            run.status = "COMPLETED";
            run.finishedAt = LocalDateTime.now();
            persistRun(run);
        });
    }

    @Transactional
    public void failRun(String runId, String routeMode, String message) {
        mutateRun(runId, run -> {
            run.routeMode = normalizeRouteMode(routeMode);
            run.status = "FAILED";
            run.finishedAt = LocalDateTime.now();
            if (StringUtils.hasText(message)) {
                appendTimeline(run, "workflow-error", "Workflow", message);
            }
            persistRun(run);
        });
    }

    @Transactional(readOnly = true)
    public List<WorkflowRunDTO> listRuns(String scene) {
        return listRuns(scene, null, true, null);
    }

    @Transactional(readOnly = true)
    public List<WorkflowRunDTO> listRuns(String scene, Long connectionId) {
        return listRuns(scene, null, true, connectionId);
    }

    @Transactional(readOnly = true)
    public List<WorkflowRunDTO> listRuns(String scene, Long userId, boolean admin, Long connectionId) {
        String normalizedScene = normalizeScene(scene);
        List<WorkflowRunEntity> entities;
        if (admin) {
            entities = connectionId == null
                    ? workflowRunRepository.findTop120BySceneOrderByStartedAtDesc(normalizedScene)
                    : workflowRunRepository.findTop120BySceneAndConnectionIdOrderByStartedAtDesc(normalizedScene, connectionId);
        } else if (connectionId == null) {
            entities = workflowRunRepository.findTop120BySceneAndUserIdOrderByStartedAtDesc(normalizedScene, userId);
        } else {
            entities = workflowRunRepository.findTop120BySceneAndUserIdAndConnectionIdOrderByStartedAtDesc(
                    normalizedScene, userId, connectionId
            );
        }
        return entities.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowRunDTO> getRun(String runId) {
        return getRun(runId, null, true, null);
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowRunDTO> getRun(String runId, Long connectionId) {
        return getRun(runId, null, true, connectionId);
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowRunDTO> getRun(String runId, Long userId, boolean admin, Long connectionId) {
        Optional<WorkflowRunEntity> entity;
        if (admin) {
            entity = connectionId == null
                    ? workflowRunRepository.findById(runId)
                    : workflowRunRepository.findByIdAndConnectionId(runId, connectionId);
        } else if (connectionId == null) {
            entity = workflowRunRepository.findByIdAndUserId(runId, userId);
        } else {
            entity = workflowRunRepository.findByIdAndUserIdAndConnectionId(runId, userId, connectionId);
        }
        return entity.map(this::toDto);
    }

    private void mutateRun(String runId, java.util.function.Consumer<MutableWorkflowRun> mutator) {
        MutableWorkflowRun run = activeRuns.get(runId);
        if (run == null) {
            run = loadRun(runId).orElse(null);
            if (run != null) {
                activeRuns.put(runId, run);
                runOrder.addFirst(runId);
                trimActiveRuns();
            }
        }
        if (run == null) {
            return;
        }
        synchronized (run) {
            mutator.accept(run);
        }
    }

    private Optional<MutableWorkflowRun> loadRun(String runId) {
        return workflowRunRepository.findById(runId).map(runEntity -> {
            MutableWorkflowRun run = new MutableWorkflowRun(
                    runEntity.getId(),
                    runEntity.getScene(),
                    runEntity.getUserId(),
                    runEntity.getConnectionId(),
                    runEntity.getTitle()
            );
            run.routeMode = normalizeRouteMode(runEntity.getRouteMode());
            run.status = defaultIfBlank(runEntity.getStatus(), "RUNNING");
            run.startedAt = runEntity.getStartedAt();
            run.finishedAt = runEntity.getFinishedAt();
            run.finalPath.addAll(readStringList(runEntity.getFinalPath()));
            run.usedAgents.addAll(readStringList(runEntity.getUsedAgents()));

            for (WorkflowStepEntity stepEntity : workflowStepRepository.findByRunIdOrderByStepOrderAsc(runId)) {
                MutableWorkflowStep step = new MutableWorkflowStep();
                step.id = stepEntity.getId();
                step.order = stepEntity.getStepOrder();
                step.agentId = normalizeAgentId(stepEntity.getAgentId());
                step.owner = defaultIfBlank(stepEntity.getOwner(), resolveOwner(step.agentId));
                step.title = defaultIfBlank(stepEntity.getTitle(), "处理步骤");
                step.kind = defaultIfBlank(stepEntity.getKind(), "Workflow Step");
                step.status = defaultIfBlank(stepEntity.getStatus(), "RUNNING");
                step.inputSummary = defaultIfBlank(stepEntity.getInputSummary(), "");
                step.outputSummary = defaultIfBlank(stepEntity.getOutputSummary(), "");
                step.tools = readStringList(stepEntity.getTools());
                step.startedAt = stepEntity.getStartedAt();
                step.finishedAt = stepEntity.getFinishedAt();
                run.stepOrder.add(step.id);
                run.steps.put(step.id, step);
            }
            run.timelineCount = workflowTimelineRepository.findByRunIdOrderByEventOrderAsc(runId).size();
            return run;
        });
    }

    private WorkflowRunDTO toDto(WorkflowRunEntity runEntity) {
        var user = runEntity.getUserId() == null
                ? null
                : sysUserRepository.findById(runEntity.getUserId()).orElse(null);

        List<WorkflowStepDTO> steps = workflowStepRepository.findByRunIdOrderByStepOrderAsc(runEntity.getId())
                .stream()
                .map(this::toStepDto)
                .toList();

        List<WorkflowTimelineItemDTO> timeline = workflowTimelineRepository.findByRunIdOrderByEventOrderAsc(runEntity.getId())
                .stream()
                .map(item -> new WorkflowTimelineItemDTO(
                        defaultIfBlank(item.getTimeLabel(), "--:--:--"),
                        defaultIfBlank(item.getNodeId(), "timeline"),
                        defaultIfBlank(item.getTitle(), "Workflow"),
                        defaultIfBlank(item.getMessage(), "")
                ))
                .toList();

        return new WorkflowRunDTO(
                runEntity.getId(),
                normalizeScene(runEntity.getScene()),
                defaultIfBlank(runEntity.getTitle(), "未命名运行"),
                runEntity.getUserId(),
                user != null ? user.getUsername() : "",
                user != null ? defaultIfBlank(user.getNickname(), user.getUsername()) : "",
                normalizeRouteMode(runEntity.getRouteMode()),
                defaultIfBlank(runEntity.getStatus(), "RUNNING"),
                resolveDuration(runEntity.getStartedAt(), runEntity.getFinishedAt()),
                runEntity.getStartedAt() == null ? "" : runEntity.getStartedAt().toString(),
                readStringList(runEntity.getFinalPath()),
                readStringList(runEntity.getUsedAgents()),
                steps,
                timeline
        );
    }

    private WorkflowStepDTO toStepDto(WorkflowStepEntity stepEntity) {
        return new WorkflowStepDTO(
                stepEntity.getId(),
                normalizeAgentId(stepEntity.getAgentId()),
                defaultIfBlank(stepEntity.getOwner(), resolveOwner(stepEntity.getAgentId())),
                defaultIfBlank(stepEntity.getTitle(), "处理步骤"),
                defaultIfBlank(stepEntity.getKind(), "Workflow Step"),
                defaultIfBlank(stepEntity.getStatus(), "RUNNING"),
                resolveDuration(stepEntity.getStartedAt(), stepEntity.getFinishedAt()),
                defaultIfBlank(stepEntity.getInputSummary(), ""),
                defaultIfBlank(stepEntity.getOutputSummary(), ""),
                readStringList(stepEntity.getTools())
        );
    }

    private void appendPath(MutableWorkflowRun run, String agentId, String owner) {
        if (!StringUtils.hasText(agentId)) {
            return;
        }
        if (!run.usedAgents.contains(agentId)) {
            run.usedAgents.add(agentId);
        }
        if (run.finalPath.isEmpty() || !owner.equals(run.finalPath.get(run.finalPath.size() - 1))) {
            run.finalPath.add(owner);
        }
    }

    private void appendTimeline(MutableWorkflowRun run, String nodeId, String title, String message) {
        WorkflowTimelineEntity entity = new WorkflowTimelineEntity();
        entity.setRunId(run.id);
        entity.setEventOrder(++run.timelineCount);
        entity.setTimeLabel(LocalDateTime.now().format(TIME_FORMATTER));
        entity.setNodeId(defaultIfBlank(nodeId, "timeline"));
        entity.setTitle(defaultIfBlank(title, "Workflow"));
        entity.setMessage(defaultIfBlank(message, "已记录流程事件"));
        workflowTimelineRepository.save(entity);
    }

    private void persistRun(MutableWorkflowRun run) {
        WorkflowRunEntity entity = workflowRunRepository.findById(run.id).orElseGet(WorkflowRunEntity::new);
        entity.setId(run.id);
        entity.setScene(normalizeScene(run.scene));
        entity.setTitle(normalizeTitle(run.title));
        entity.setUserId(run.userId);
        entity.setConnectionId(run.connectionId);
        entity.setRouteMode(normalizeRouteMode(run.routeMode));
        entity.setStatus(defaultIfBlank(run.status, "RUNNING"));
        entity.setStartedAt(run.startedAt == null ? LocalDateTime.now() : run.startedAt);
        entity.setFinishedAt(run.finishedAt);
        entity.setFinalPath(writeStringList(run.finalPath));
        entity.setUsedAgents(writeStringList(run.usedAgents));
        workflowRunRepository.save(entity);
    }

    private void persistStep(String runId, MutableWorkflowStep step) {
        WorkflowStepEntity entity = workflowStepRepository.findById(step.id).orElseGet(WorkflowStepEntity::new);
        entity.setId(step.id);
        entity.setRunId(runId);
        entity.setStepOrder(step.order);
        entity.setAgentId(normalizeAgentId(step.agentId));
        entity.setOwner(defaultIfBlank(step.owner, resolveOwner(step.agentId)));
        entity.setTitle(defaultIfBlank(step.title, "处理步骤"));
        entity.setKind(defaultIfBlank(step.kind, "Workflow Step"));
        entity.setStatus(defaultIfBlank(step.status, "RUNNING"));
        entity.setInputSummary(defaultIfBlank(step.inputSummary, ""));
        entity.setOutputSummary(defaultIfBlank(step.outputSummary, ""));
        entity.setTools(writeStringList(step.tools));
        entity.setStartedAt(step.startedAt == null ? LocalDateTime.now() : step.startedAt);
        entity.setFinishedAt(step.finishedAt);
        workflowStepRepository.save(entity);
    }

    private void trimActiveRuns() {
        while (runOrder.size() > MAX_ACTIVE_RUNS) {
            String removedRunId = runOrder.pollLast();
            if (removedRunId != null) {
                activeRuns.remove(removedRunId);
            }
        }
    }

    private List<String> normalizeTools(List<String> tools) {
        if (tools == null || tools.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tool : tools) {
            if (StringUtils.hasText(tool)) {
                normalized.add(tool.trim());
            }
        }
        return new ArrayList<>(normalized);
    }

    private List<String> mergeTools(List<String> existing, List<String> appended) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(normalizeTools(existing));
        merged.addAll(normalizeTools(appended));
        return new ArrayList<>(merged);
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(objectMapper.readValue(json, STRING_LIST_TYPE));
        } catch (Exception e) {
            log.warn("Failed to read workflow string list", e);
            return new ArrayList<>();
        }
    }

    private String writeStringList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception e) {
            log.warn("Failed to write workflow string list", e);
            return "[]";
        }
    }

    private long resolveDuration(LocalDateTime startedAt, LocalDateTime finishedAt) {
        if (startedAt == null) {
            return 0L;
        }
        LocalDateTime endTime = finishedAt == null ? LocalDateTime.now() : finishedAt;
        return Math.max(Duration.between(startedAt, endTime).toMillis(), 0L);
    }

    private String normalizeScene(String scene) {
        if (!StringUtils.hasText(scene)) {
            return "chat";
        }
        return scene.trim().toLowerCase();
    }

    private String normalizeRouteMode(String routeMode) {
        return defaultIfBlank(routeMode, "SINGLE_AGENT");
    }

    private String normalizeAgentId(String agentId) {
        return defaultIfBlank(agentId, "assistant");
    }

    private String resolveOwner(String agentId) {
        String normalized = normalizeAgentId(agentId);
        return switch (normalized) {
            case "assistant" -> "AssistantAgent";
            case "knowledge" -> "KnowledgeAgent";
            case "data" -> "DataAgent";
            default -> normalized;
        };
    }

    private String normalizeTitle(String title) {
        String normalized = defaultIfBlank(title, "未命名运行").replaceAll("\\s+", " ").trim();
        return normalized.length() <= 40 ? normalized : normalized.substring(0, 40) + "...";
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static final class MutableWorkflowRun {
        private final String id;
        private final String scene;
        private final Long userId;
        private final Long connectionId;
        private final String title;
        private final Map<String, MutableWorkflowStep> steps = new LinkedHashMap<>();
        private final List<String> stepOrder = new ArrayList<>();
        private final List<String> finalPath = new ArrayList<>();
        private final List<String> usedAgents = new ArrayList<>();

        private String routeMode = "SINGLE_AGENT";
        private String status = "RUNNING";
        private LocalDateTime startedAt = LocalDateTime.now();
        private LocalDateTime finishedAt;
        private int timelineCount;

        private MutableWorkflowRun(String id, String scene, Long userId, Long connectionId, String title) {
            this.id = id;
            this.scene = scene;
            this.userId = userId;
            this.connectionId = connectionId;
            this.title = title;
        }
    }

    private static final class MutableWorkflowStep {
        private String id;
        private Integer order;
        private String agentId;
        private String owner;
        private String title;
        private String kind;
        private String status;
        private String inputSummary;
        private String outputSummary;
        private List<String> tools = new ArrayList<>();
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
    }
}
