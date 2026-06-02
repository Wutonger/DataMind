package com.datamine.analysis.web.controller;

import com.datamine.analysis.agent.workflow.WorkflowRunTracker;
import com.datamine.analysis.common.dto.workflow.WorkflowRunDTO;
import com.datamine.analysis.core.service.ConnectionAccessService;
import com.datamine.analysis.core.service.CurrentUserService;
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
    private final CurrentUserService currentUserService;
    private final ConnectionAccessService connectionAccessService;

    @GetMapping("/runs")
    public ResponseEntity<List<WorkflowRunDTO>> listRuns(@RequestParam(defaultValue = "chat") String scene,
                                                         @RequestParam(required = false) Long connectionId) {
        Long userId = currentUserService.getRequiredUserId();
        boolean admin = currentUserService.isAdmin();
        if (connectionId != null) {
            connectionAccessService.checkConnectionAccessible(userId, admin, connectionId);
        }
        return ResponseEntity.ok(workflowRunTracker.listRuns(scene, userId, admin, connectionId));
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<WorkflowRunDTO> getRun(@PathVariable String runId,
                                                 @RequestParam(required = false) Long connectionId) {
        Long userId = currentUserService.getRequiredUserId();
        boolean admin = currentUserService.isAdmin();
        if (connectionId != null) {
            connectionAccessService.checkConnectionAccessible(userId, admin, connectionId);
        }
        return workflowRunTracker.getRun(runId, userId, admin, connectionId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
