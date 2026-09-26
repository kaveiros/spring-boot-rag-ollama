package com.nikchant.rag.controller;

import org.springframework.ai.chat.client.ChatClient;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final ChatClient chatClient;

    private ChatController(ChatClient.Builder builder) {
        this.chatClient =   builder.build();
    }

    @GetMapping("/chat")
    public ResponseEntity<String> chat(@RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
        // This sends the message to Ollama and returns the text response
        return ResponseEntity.ok(chatClient.prompt()
                .user(message)
                .call()
                .content());
    }

}
