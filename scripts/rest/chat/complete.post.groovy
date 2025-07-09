package com.example.chatbot

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.mcp.client.McpClient
import org.springframework.ai.mcp.client.McpSyncClientCustomizer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController



class ChatClientConfig {
    @Value('${spring.ai.openai.api-key}')
    String openAiApiKey

    ChatClient chatClient(ChatClient.Builder chatClientBuilder, ToolCallbackProvider toolCallbackProvider) {
        chatClientBuilder
            .defaultToolCallbacks([toolCallbackProvider])
            .build()
    }

    OpenAiChatModel chatModel() {
        new OpenAiChatModel(
            openAiApiKey,
            OpenAiChatOptions.builder()
                .withModel('gpt-4o-mini')
                .build()
        )
    }

    McpSyncClientCustomizer mcpClientCustomizer() {
        new McpSyncClientCustomizer() {
            @Override
            void customize(String serverConfigurationName, McpClient.SyncSpec spec) {
                spec.requestTimeout(Duration.ofSeconds(30))
            }
        }
    }
}

class ChatController {
    final ChatClient chatClient

    ChatController(ChatClient chatClient) {
        this.chatClient = chatClient
    }

    @PostMapping('/chat')
    String chat(@RequestBody Map<String, String> request) {
        def query = request.get('question')
        if (!query) {
            return "Error: 'question' field is required"
        }
        chatClient.prompt()
            .user(query)
            .call()
            .content()
    }
}