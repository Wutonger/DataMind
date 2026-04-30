package com.datamine.analysis.common.dto.workflow;

import java.util.List;

public record WorkflowStepDTO(
        String id,
        String agentId,
        String owner,
        String title,
        String kind,
        String status,
        long durationMs,
        String inputSummary,
        String outputSummary,
        List<String> tools
) {
}
