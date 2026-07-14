package com.pravoos.notification.service;

import com.pravoos.notification.bot.PravoOsAdminBot;
import com.pravoos.notification.config.TelegramBotProperties;
import com.pravoos.notification.event.NewLoginKafkaPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramNotificationServiceTest {

    @Mock private PravoOsAdminBot bot;
    @Mock private TelegramChatIdResolver telegramChatIdResolver;

    private TelegramNotificationService service;

    @BeforeEach
    void setUp() {
        TelegramBotProperties properties = new TelegramBotProperties("token", "bot", List.of("1"));
        service = new TelegramNotificationService(bot, properties, telegramChatIdResolver);
    }

    @Test
    void sendNewLogin_sendsMessageToResolvedChat() throws Exception {
        UUID userId = UUID.randomUUID();
        when(telegramChatIdResolver.resolve(userId)).thenReturn(Optional.of(987654L));

        boolean delivered = service.sendNewLogin(payload(userId));

        assertThat(delivered).isTrue();
        ArgumentCaptor<SendMessage> captor = ArgumentCaptor.forClass(SendMessage.class);
        verify(bot).execute(captor.capture());
        SendMessage sent = captor.getValue();
        assertThat(sent.getChatId()).isEqualTo("987654");
        assertThat(sent.getText()).contains("Новый вход в аккаунт");
        assertThat(sent.getText()).contains("203.0.113.9");
        assertThat(sent.getText()).contains("JUnit-UA");
    }

    @Test
    void sendNewLogin_skips_whenTelegramNotLinked() throws Exception {
        UUID userId = UUID.randomUUID();
        when(telegramChatIdResolver.resolve(userId)).thenReturn(Optional.empty());

        boolean delivered = service.sendNewLogin(payload(userId));

        assertThat(delivered).isFalse();
        verify(bot, never()).execute(any(SendMessage.class));
    }

    private NewLoginKafkaPayload payload(UUID userId) {
        return new NewLoginKafkaPayload(userId, "203.0.113.9", "JUnit-UA", "2026-07-03 10:15", true, true);
    }
}
