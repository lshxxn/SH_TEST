package com.example.newsbot.delivery.solapi;

import java.time.Clock;

import com.example.newsbot.config.NewsBotProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "newsbot.solapi.enabled", havingValue = "true")
public class SolapiConfig {

    @Bean
    SolapiBrandMessageSender solapiBrandMessageSender(SolapiProperties properties, NewsBotProperties newsBotProperties,
                                                      Clock clock, RestClient.Builder newsBotRestClientBuilder,
                                                      JsonMapper jsonMapper) {
        return new SolapiBrandMessageSender(properties, newsBotProperties.news().moreLink(),
                newsBotProperties.schedule().zone(), clock, newsBotRestClientBuilder, jsonMapper);
    }
}
