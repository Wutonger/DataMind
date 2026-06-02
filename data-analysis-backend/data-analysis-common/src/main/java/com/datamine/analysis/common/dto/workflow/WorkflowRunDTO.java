package com.datamine.analysis.common.dto.workflow;

import java.util.List;

public record WorkflowRunDTO(
        String id,
        String scene,
        String title,
        Long userId,
        String username,
        String nickname,
        String routeMode,
        String status,
        long totalDurationMs,
        String startedAt,
        List<String> finalPath,
        List<String> usedAgents,
        List<WorkflowStepDTO> steps,
        List<WorkflowTimelineItemDTO> timeline
) {
}
