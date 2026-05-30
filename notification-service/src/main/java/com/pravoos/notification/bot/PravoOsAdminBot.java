package com.pravoos.notification.bot;

import com.pravoos.notification.config.TelegramBotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class PravoOsAdminBot extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(PravoOsAdminBot.class);

    private final String botUsername;

    public PravoOsAdminBot(TelegramBotProperties botProperties) {
        super(botProperties.token());
        this.botUsername = botProperties.username();
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        log.debug("Received update: {}", update.getUpdateId());
    }
}
