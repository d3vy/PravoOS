package com.pravoos.user.collaboration.internal.controller;

import com.pravoos.user.collaboration.internal.dto.CaseMessageNotificationRequest;
import com.pravoos.user.collaboration.internal.dto.CaseMessageNotificationResult;
import com.pravoos.user.collaboration.internal.dto.DeadlineEmailRequest;
import com.pravoos.user.collaboration.internal.service.CaseMessageNotificationService;
import com.pravoos.user.collaboration.internal.service.DeadlineNotificationEmailService;
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
