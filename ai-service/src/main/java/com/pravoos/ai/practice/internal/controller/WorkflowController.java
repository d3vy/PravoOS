package com.pravoos.ai.practice.internal.controller;

import com.pravoos.ai.practice.internal.dto.WorkflowInfo;
import com.pravoos.ai.practice.internal.service.WorkflowService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping
    public ResponseEntity<List<WorkflowInfo>> list() {
        return ResponseEntity.ok(workflowService.listWorkflows());
    }
}
