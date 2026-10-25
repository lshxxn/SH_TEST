package com.example.newsbot.chatbot;

import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.example.newsbot.ai.ChatMemory;
import com.example.newsbot.ai.GeminiClient;
import com.example.newsbot.ai.GeminiProperties;
import com.example.newsbot.ai.GeminiStatus;
import com.example.newsbot.config.NewsBotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
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
    RecentErrors recentErrors(Clock clock, NewsBotProperties properties) {
        return new RecentErrors(clock, properties.schedule().zone());
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

    @Bean
    GeminiStatus geminiStatus(GeminiProperties gemini, GeminiClient geminiClient, Clock clock) {
        return new GeminiStatus(gemini, geminiClient, Path.of(""), clock);
    }

    /** 프로그램이 켜지면 눈에 띄는 안내 상자로 준비 상태를 알려 준다. */
    @Bean
    StartupBanner startupBanner(GeminiStatus geminiStatus, Environment environment) {
        return new StartupBanner(geminiStatus, environment);
    }

    static final class StartupBanner {
        private final GeminiStatus status;
        private final Environment environment;

        StartupBanner(GeminiStatus status, Environment environment) {
            this.status = status;
            this.environment = environment;
        }

        @EventListener(ApplicationReadyEvent.class)
        void show() {
            String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));
            GeminiStatus.Result gemini = status.check();
            String mark = switch (gemini.state()) {
                case OK -> "[정상]";
                case NO_KEY -> "[꺼짐]";
                case FAILED -> "[문제]";
            };
            String line = "=".repeat(60);
            String banner = String.join(System.lineSeparator(),
                    "", line,
                    "  뉴스 챗봇 서버가 켜졌습니다",
                    "  - 뉴스 스킬     http://localhost:" + port + "/kakao/skill/news",
                    "  - AI 대화 스킬  http://localhost:" + port + "/kakao/skill/chat",
                    "  - 제미나이 " + mark + " " + gemini.message(),
                    line, "");
            if (gemini.state() == GeminiStatus.State.OK) {
                log.info(banner);
            } else {
                log.warn(banner);
            }
        }
    }
}
