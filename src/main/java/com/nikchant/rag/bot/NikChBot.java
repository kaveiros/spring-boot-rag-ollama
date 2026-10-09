package com.nikchant.rag.bot;

import jakarta.annotation.PreDestroy;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import com.nikchant.rag.services.pgvector.VectorService;
import com.nikchant.rag.services.tika.TikaService;

import java.io.FileWriter;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    private final TikaService tikaService;
    private final VectorService vectorService;
   // private final TelegramBot bot;


    public NikChBot(ChatClient.Builder builder, @Value("${telegram.bot.token}") String token, TikaService tikaService,
            VectorService vectorService) {
        this.token = token;
        this.chatClient = builder.build();
        this.telegramClient = new OkHttpTelegramClient(getBotToken());
        //this.bot = new TelegramBot.Builder(getBotToken()).okHttpClient(this.telegramClient).build();
        this.tikaService = tikaService;
        this.vectorService = vectorService;
    }


    private String askModel(String question, long userId) {
        // Find the stored chunks that are relevant to the question
        String context = vectorService.search(question, 4, userId).stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));

        var prompt = chatClient.prompt().user(question);
        if (!context.isEmpty()) {
            prompt = prompt.system("Answer using the following context when it is relevant:\n" + context);
        }
        String reply = prompt.call().content();
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
        if ((update.hasMessage() && update.getMessage().hasText())) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            long userId = update.getMessage().getChat().getId();
            logger.info("USER ID FOR THIS CHAT IS {}", userId);
            if (messageText != null && !messageText.isEmpty()) {
                String modelAnswer = askModel(messageText, userId);
                sendToTelegram(chatId, modelAnswer);
            }
        }
        
            // Set variables

            if (update.getMessage().hasDocument()) {
                logger.info(update.getMessage().getDocument().getFileName());
                var fileId = update.getMessage().getDocument().getFileId();
                try {  

                    GetFile getFile = new GetFile(fileId);
                    File f = telegramClient.execute(getFile);
                    
                   // InputStream file = telegramClient.downloadFileAsStream(f);

                    List<Document> content = tikaService.readDocument(f.getFileUrl(getBotToken()));
                    String fileName = update.getMessage().getDocument().getFileName();
                    long userId = update.getMessage().getChat().getId();
                    vectorService.addToStore(content, fileName, userId);
                    sendToTelegram(update.getMessage().getChatId(), "Stored " + fileName);

                   // FileUtils.writeByteArrayToFile(new java.io.File(update.getMessage().getDocument().getFileName()), IOUtils.toByteArray(file));
                    
                } catch (Exception e) {
                    logger.error(e.getMessage());
                }
            }
            

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
