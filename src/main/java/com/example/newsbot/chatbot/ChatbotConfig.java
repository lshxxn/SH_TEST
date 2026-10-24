package com.example.newsbot.chatbot;

import java.time.Clock;

import com.example.newsbot.config.NewsBotProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "newsbot.chatbot.enabled", havingValue = "true", matchIfMissing = true)
public class ChatbotConfig {

    @Bean
    NewsSkillService newsSkillService(NewsBotProperties properties, ChatbotProperties chatbot, Clock clock,
                                      RestClient.Builder newsBotRestClientBuilder) {
        return new NewsSkillService(properties, chatbot, clock,
                NewsSkillService.createRestClient(newsBotRestClientBuilder, chatbot));
    }
}
