package com.pravoos.notification.config;

import com.pravoos.notification.bot.PravoOsAdminBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
public class TelegramBotRegistrationConfig {

  private static final Logger log = LoggerFactory.getLogger(TelegramBotRegistrationConfig.class);
  private static final String REGISTRATION_THREAD_NAME = "telegram-bot-registration";

  private final PravoOsAdminBot bot;
  private final TelegramBotsApi telegramBotsApi;

  public TelegramBotRegistrationConfig(PravoOsAdminBot bot) throws TelegramApiException {
    this.bot = bot;
    this.telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
  }

  @Bean
  public TelegramBotsApi telegramBotsApi() {
    return telegramBotsApi;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void registerBotAfterStartup() {
    Thread registrationThread = new Thread(this::registerBot, REGISTRATION_THREAD_NAME);
    registrationThread.setDaemon(true);
    registrationThread.start();
  }

  private void registerBot() {
    try {
      telegramBotsApi.registerBot(bot);
      log.info("Telegram bot [{}] registered for long polling", bot.getBotUsername());
    } catch (TelegramApiException exception) {
      log.error(
          "Telegram bot registration failed — notifications via Telegram are disabled, "
              + "the rest of the service keeps running",
          exception);
    }
  }
}
