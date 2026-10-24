package com.example.newsbot.delivery.kakao;

import java.time.Clock;

import com.example.newsbot.config.NewsBotProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "newsbot.kakao.enabled", havingValue = "true")
public class KakaoConfig {

    @Bean
    KakaoOAuthClient kakaoOAuthClient(KakaoProperties properties, RestClient.Builder newsBotRestClientBuilder) {
        return new KakaoOAuthClient(properties, newsBotRestClientBuilder);
    }

    @Bean
    KakaoTokenService kakaoTokenService(KakaoOAuthClient oauthClient, KakaoProperties properties,
                                        JsonMapper jsonMapper, Clock clock) {
        return new KakaoTokenService(oauthClient, new KakaoTokenStore(properties.tokenFile(), jsonMapper), clock);
    }

    @Bean
    KakaoMemoSender kakaoMemoSender(KakaoProperties properties, KakaoTokenService tokenService,
                                    NewsBotProperties newsBotProperties, RestClient.Builder newsBotRestClientBuilder,
                                    JsonMapper jsonMapper) {
        KakaoTemplateFactory templateFactory = new KakaoTemplateFactory(
                newsBotProperties.news().moreLink(), newsBotProperties.schedule().zone());
        return new KakaoMemoSender(properties, tokenService, templateFactory, newsBotRestClientBuilder, jsonMapper);
    }
}
