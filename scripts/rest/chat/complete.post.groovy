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

import org.springframework.ai.chat.client.advisor.api.Advisor
import org.springframework.ai.chat.client.advisor.api.AdvisorContext
import org.springframework.ai.chat.client.advisor.api.AdvisorResponse
import org.springframework.ai.chat.client.advisor.api.ChatClientAdvisor
import org.springframework.stereotype.Component


def apiKey = System.getenv("crafter_chatgpt")

def query = params.question

if(!query) {
    return "Error: 'question' field is required"
}

def toolCallbackProvider = new ToolCallbackProvider()
def chatModel = OpenAiChatModel(apiKey, OpenAiChatOptions.builder().withModel('gpt-4o-mini').build())

def chatClient = chatClientBuilder.defaultToolCallbacks([toolCallbackProvider]).build()

chatClient.prompt().user(query).call().content()
            


class ToolCallbackProvider implements ChatClientAdvisor {
    AdvisorResponse advise(AdvisorContext context, Advisor chain) {
        def tools = [
            [
                type: 'function',
                function: [
                    name: 'get_weather',
                    description: 'Get the current weather for a location',
                    parameters: [
                        type: 'object',
                        properties: [
                            location: [
                                type: 'string',
                                description: 'The city and state, e.g., New York, NY'
                            ]
                        ],
                        required: ['location']
                    ]
                ]
            ]
        ]
        context.chatClient().prompt().tools(tools)
        chain.advise(context)
    }
}
