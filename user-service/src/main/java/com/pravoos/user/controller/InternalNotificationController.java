package com.pravoos.user.controller;

import com.pravoos.user.model.dto.DeadlineEmailRequest;
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

    public InternalNotificationController(DeadlineNotificationEmailService deadlineNotificationEmailService) {
        this.deadlineNotificationEmailService = deadlineNotificationEmailService;
    }

    @PostMapping("/deadline-email")
    public ResponseEntity<Void> deadlineEmail(@Valid @RequestBody DeadlineEmailRequest request) {
        deadlineNotificationEmailService.sendDeadlineEmail(request);
        return ResponseEntity.ok().build();
    }
}
