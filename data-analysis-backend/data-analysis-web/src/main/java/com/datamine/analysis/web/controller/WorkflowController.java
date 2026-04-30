package com.datamine.analysis.web.controller;

import com.datamine.analysis.agent.workflow.WorkflowRunTracker;
import com.datamine.analysis.common.dto.workflow.WorkflowRunDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowRunTracker workflowRunTracker;

    @GetMapping("/runs")
    public ResponseEntity<List<WorkflowRunDTO>> listRuns(@RequestParam(defaultValue = "chat") String scene,
                                                         @RequestParam(required = false) Long connectionId) {
        return ResponseEntity.ok(workflowRunTracker.listRuns(scene, connectionId));
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<WorkflowRunDTO> getRun(@PathVariable String runId,
                                                 @RequestParam(required = false) Long connectionId) {
        return workflowRunTracker.getRun(runId, connectionId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
