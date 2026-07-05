package com.pravoos.user.collaboration.internal.controller;

import com.pravoos.common.web.SecurityUtils;
import com.pravoos.user.collaboration.internal.dto.TelegramLinkResponse;
import com.pravoos.user.collaboration.internal.service.TelegramLinkService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class TelegramUserController {

    private final TelegramLinkService telegramLinkService;

    public TelegramUserController(TelegramLinkService telegramLinkService) {
        this.telegramLinkService = telegramLinkService;
    }

    @PostMapping("/profile/telegram/link-code")
    public ResponseEntity<TelegramLinkResponse> createTelegramLinkCode(Authentication authentication) {
        return ResponseEntity.ok(telegramLinkService.createLinkCode(SecurityUtils.currentUserId(authentication)));
    }

    @DeleteMapping("/profile/telegram")
    public ResponseEntity<Void> unlinkTelegram(Authentication authentication) {
        telegramLinkService.unlink(SecurityUtils.currentUserId(authentication));
        return ResponseEntity.noContent().build();
    }
}
