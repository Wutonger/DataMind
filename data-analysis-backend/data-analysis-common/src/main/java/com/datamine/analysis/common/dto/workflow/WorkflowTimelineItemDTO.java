package com.datamine.analysis.common.dto.workflow;

public record WorkflowTimelineItemDTO(
        String time,
        String nodeId,
        String title,
        String message
) {
}
