package com.pravoos.user.controller;

import com.pravoos.user.model.dto.BindTelegramRequest;
import com.pravoos.user.model.dto.BindTelegramResponse;
import com.pravoos.user.model.dto.TelegramChatIdResponse;
import com.pravoos.user.security.InternalSecretVerifier;
import com.pravoos.user.service.TelegramLinkService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/telegram")
public class TelegramInternalController {

    private final TelegramLinkService telegramLinkService;
    private final InternalSecretVerifier secretVerifier;

    public TelegramInternalController(TelegramLinkService telegramLinkService,
                                      InternalSecretVerifier secretVerifier) {
        this.telegramLinkService = telegramLinkService;
        this.secretVerifier = secretVerifier;
    }

    @PostMapping("/bind")
    public ResponseEntity<BindTelegramResponse> bind(@Valid @RequestBody BindTelegramRequest request,
                                                     @RequestHeader("X-Internal-Secret") String secret) {
        secretVerifier.verify(secret);
        String fullName = telegramLinkService.bind(request.code(), request.chatId());
        return ResponseEntity.ok(new BindTelegramResponse(fullName));
    }

    @GetMapping("/chat-id/{lawyerId}")
    public ResponseEntity<TelegramChatIdResponse> chatId(@PathVariable UUID lawyerId,
                                                         @RequestHeader("X-Internal-Secret") String secret) {
        secretVerifier.verify(secret);
        return telegramLinkService.resolveChatId(lawyerId)
                .map(id -> ResponseEntity.ok(new TelegramChatIdResponse(id)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
