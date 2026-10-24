package com.example.newsbot.chatbot;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.newsbot.ai.ChatMemory;
import com.example.newsbot.ai.GeminiClient;
import com.example.newsbot.ai.GeminiProperties;
import com.example.newsbot.config.NewsBotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "newsbot.chatbot.enabled", havingValue = "true", matchIfMissing = true)
public class ChatbotConfig {

    private static final Logger log = LoggerFactory.getLogger(ChatbotConfig.class);

    @Bean
    SkillTokenGuard skillTokenGuard(ChatbotProperties chatbot) {
        return new SkillTokenGuard(chatbot.skillToken());
    }

    @Bean
    NewsSkillService newsSkillService(NewsBotProperties properties, ChatbotProperties chatbot, Clock clock,
                                      RestClient.Builder newsBotRestClientBuilder) {
        return new NewsSkillService(properties, chatbot, clock,
                NewsSkillService.createRestClient(newsBotRestClientBuilder, chatbot));
    }

    @Bean
    GeminiClient geminiClient(GeminiProperties gemini, JsonMapper jsonMapper, RestClient.Builder newsBotRestClientBuilder) {
        return GeminiClient.create(gemini, jsonMapper, newsBotRestClientBuilder);
    }

    @Bean(destroyMethod = "close")
    ExecutorService chatCallbackExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    ChatSkillService chatSkillService(GeminiProperties gemini, GeminiClient geminiClient, NewsSkillService news,
                                      ExecutorService chatCallbackExecutor, RestClient.Builder newsBotRestClientBuilder,
                                      JsonMapper jsonMapper, Clock clock) {
        return new ChatSkillService(gemini, geminiClient, new ChatMemory(gemini.historyTurns(), gemini.historyTtl()),
                news, chatCallbackExecutor, newsBotRestClientBuilder.clone().build(), jsonMapper, clock);
    }

    /** 프로그램이 켜지면 제미나이 키와 모델을 확인해 결과를 화면(로그)에 알려 준다. */
    @Bean
    GeminiStartupCheck geminiStartupCheck(GeminiProperties gemini, GeminiClient geminiClient,
                                          ExecutorService chatCallbackExecutor) {
        return new GeminiStartupCheck(gemini, geminiClient, chatCallbackExecutor);
    }

    static final class GeminiStartupCheck {
        private final GeminiProperties gemini;
        private final GeminiClient client;
        private final ExecutorService executor;

        GeminiStartupCheck(GeminiProperties gemini, GeminiClient client, ExecutorService executor) {
            this.gemini = gemini;
            this.client = client;
            this.executor = executor;
        }

        @EventListener(ApplicationReadyEvent.class)
        void check() {
            if (!gemini.configured()) {
                log.info("제미나이: GEMINI_API_KEY 가 없어 AI 대화는 꺼져 있습니다. (secrets.properties 에 넣으면 켜집니다)");
                return;
            }
            executor.execute(() -> {
                try {
                    log.info("제미나이 연결 확인 완료: {} ({})", client.checkModel(), gemini.modelName());
                } catch (RuntimeException e) {
                    log.warn("제미나이 연결 확인 실패: {}", e.getMessage());
                }
            });
        }
    }
}
