package com.pravoos.notification.config;

import com.pravoos.notification.bot.PravoOsAdminBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
public class TelegramBotRegistrationConfig {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotRegistrationConfig.class);

    @Bean
    public TelegramBotsApi telegramBotsApi(PravoOsAdminBot bot) throws TelegramApiException {
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        telegramBotsApi.registerBot(bot);
        log.info("Telegram bot [{}] registered for long polling", bot.getBotUsername());
        return telegramBotsApi;
    }
}
