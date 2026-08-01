package com.pravoos.notification.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Component
public class TelegramBotRegistrar {

  private static final Logger log = LoggerFactory.getLogger(TelegramBotRegistrar.class);
  private static final String REGISTRATION_THREAD_NAME = "telegram-bot-registration";

  private final TelegramBotsApi telegramBotsApi;
  private final PravoOsAdminBot bot;

  public TelegramBotRegistrar(TelegramBotsApi telegramBotsApi, PravoOsAdminBot bot) {
    this.telegramBotsApi = telegramBotsApi;
    this.bot = bot;
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
          "Telegram bot registration failed — Telegram notifications are disabled, "
              + "the rest of the service keeps running",
          exception);
    }
  }
}
