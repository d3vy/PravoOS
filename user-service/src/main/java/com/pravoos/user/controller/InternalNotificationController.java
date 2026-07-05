package com.pravoos.user.controller;

import com.pravoos.user.model.dto.CaseMessageNotificationRequest;
import com.pravoos.user.model.dto.CaseMessageNotificationResult;
import com.pravoos.user.model.dto.DeadlineEmailRequest;
import com.pravoos.user.service.CaseMessageNotificationService;
import com.pravoos.user.service.DeadlineNotificationEmailService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/notifications")
public class InternalNotificationController {

    private final DeadlineNotificationEmailService deadlineNotificationEmailService;
    private final CaseMessageNotificationService caseMessageNotificationService;

    public InternalNotificationController(DeadlineNotificationEmailService deadlineNotificationEmailService,
                                          CaseMessageNotificationService caseMessageNotificationService) {
        this.deadlineNotificationEmailService = deadlineNotificationEmailService;
        this.caseMessageNotificationService = caseMessageNotificationService;
    }

    @PostMapping("/deadline-email")
    public ResponseEntity<Void> deadlineEmail(@Valid @RequestBody DeadlineEmailRequest request) {
        deadlineNotificationEmailService.sendDeadlineEmail(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/case-message")
    public ResponseEntity<CaseMessageNotificationResult> caseMessage(
            @Valid @RequestBody CaseMessageNotificationRequest request) {
        return ResponseEntity.ok(caseMessageNotificationService.dispatch(request));
    }
}
