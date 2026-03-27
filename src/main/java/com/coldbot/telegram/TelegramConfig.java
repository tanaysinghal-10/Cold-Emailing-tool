package com.coldbot.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;

@Slf4j
@Configuration
public class TelegramConfig {

    @Value("${telegram.bot.token}")
    private String botToken;

    @Bean
    public TelegramBotsLongPollingApplication telegramBotsApplication(ColdEmailBot bot) {
        TelegramBotsLongPollingApplication application = new TelegramBotsLongPollingApplication();
        try {
            application.registerBot(botToken, bot);
            log.info("Telegram bot registered successfully");
        } catch (Exception e) {
            log.error("Failed to register Telegram bot: {}", e.getMessage(), e);
            throw new RuntimeException("Could not register Telegram bot", e);
        }
        return application;
    }
}
