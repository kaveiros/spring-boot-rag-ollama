package com.nikchant.rag.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

/**
 * Class for communicating with telegram bot.
 */
@Component
public class NikChBot implements SpringLongPollingBot, LongPollingUpdateConsumer {

    Logger logger = LoggerFactory.getLogger(NikChBot.class);

    private final TelegramClient telegramClient;
    private final ChatClient chatClient;
    private final String token;


    public NikChBot(ChatClient.Builder builder, @Value("${telegram.bot.token}") String token) {
        this.token = token;
        this.chatClient = builder.build();
        this.telegramClient = new OkHttpTelegramClient(getBotToken());;
    }




    @Override
    public void consume(List<Update> updates) {
        updates.forEach(update -> {
            // We check if the update has a message and the message has text
            if (update.hasMessage() && update.getMessage().hasText()) {
                // Set variables
                String message_text = update.getMessage().getText();

                String messageText = chatClient.prompt()
                        .user(message_text)
                        .call()
                        .content();
                long chat_id = update.getMessage().getChatId();

                if (messageText != null) {
                    SendMessage message = SendMessage // Create a message object
                            .builder()
                            .chatId(chat_id)
                            .text(messageText)
                            .build();
                    try {
                        telegramClient.execute(message); // Sending our message object to user
                    } catch (TelegramApiException e) {
                        logger.error("Could not communicate with telegram... Reason {}", e.getMessage());
                    }
                }

            }
        });

        }


    @Override
    public String getBotToken() {
        return token;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }
}
