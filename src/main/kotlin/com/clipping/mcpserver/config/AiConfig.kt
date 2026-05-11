package com.clipping.mcpserver.config

import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.model.ChatModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AiConfig {

    @Bean
    fun chatClient(chatModel: ChatModel): ChatClient =
        ChatClient.builder(chatModel).build()
}
