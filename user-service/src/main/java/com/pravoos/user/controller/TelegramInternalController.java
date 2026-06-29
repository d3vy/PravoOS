package com.pravoos.user.controller;

import com.pravoos.user.model.dto.BindTelegramRequest;
import com.pravoos.user.model.dto.BindTelegramResponse;
import com.pravoos.user.model.dto.TelegramChatIdResponse;
import com.pravoos.user.service.TelegramLinkService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/internal/telegram")
public class TelegramInternalController {

    private final TelegramLinkService telegramLinkService;

    public TelegramInternalController(TelegramLinkService telegramLinkService) {
        this.telegramLinkService = telegramLinkService;
    }

    @PostMapping("/bind")
    public ResponseEntity<BindTelegramResponse> bind(@Valid @RequestBody BindTelegramRequest request) {
        String fullName = telegramLinkService.bind(request.code(), request.chatId());
        return ResponseEntity.ok(new BindTelegramResponse(fullName));
    }

    @GetMapping("/chat-id/{lawyerId}")
    public ResponseEntity<TelegramChatIdResponse> chatId(@PathVariable UUID lawyerId) {
        return telegramLinkService.resolveChatId(lawyerId)
                .map(id -> ResponseEntity.ok(new TelegramChatIdResponse(id)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
