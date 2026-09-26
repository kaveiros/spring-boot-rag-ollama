package com.nikchant.rag.bot;

import jakarta.annotation.PreDestroy;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Class for communicating with telegram bot.
 */
@Component
public class NikChBot implements SpringLongPollingBot, LongPollingUpdateConsumer {

    Logger logger = LoggerFactory.getLogger(NikChBot.class);

    private final TelegramClient telegramClient;
    private final ChatClient chatClient;
    private final String token;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);


    public NikChBot(ChatClient.Builder builder, @Value("${telegram.bot.token}") String token) {
        this.token = token;
        this.chatClient = builder.build();
        this.telegramClient = new OkHttpTelegramClient(getBotToken());;
    }


    private String askModel(String question) {
        String reply =  chatClient.prompt()
                .user(question)
                .call()
                .content();
        return (reply == null || reply.isBlank()) ? "I could not produce answer for that." : reply;
    }


    private void sendToTelegram(long chatId, String messageText) {
        SendMessage message = SendMessage // Create a message object
                .builder()
                .chatId(chatId)
                .text(messageText)
                .build();
        try {
            telegramClient.execute(message); // Sending our message object to user
        } catch (TelegramApiException e) {
            logger.error("Could not communicate with telegram... Reason {}", e.getMessage());
        }
    }

    private void handle(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
            // Set variables
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            String modelAnswer = askModel(messageText);
            sendToTelegram(chatId, modelAnswer);
    }

    @Override
    public void consume(List<Update> updates) {
        updates.forEach(update -> executor.submit(() -> {handle(update);}));
    }


    @Override
    public String getBotToken() {
        return token;
    }


    @PreDestroy
    void shutdown() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }
}
