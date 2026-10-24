package com.example.newsbot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

public class GeminiClientTest {

    public static final String URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent";

    private MockRestServiceServer server;
    private GeminiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient rc = builder.build();
        client = new GeminiClient(properties("test-key", "models/gemini-flash-lite-latest"), JsonMapper.builder().build(), rc, rc);
    }

    @Test
    void 이전_대화와_질문을_보내고_답변_글자만_꺼낸다() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value("짧게 답해"))
                .andExpect(jsonPath("$.contents.length()").value(3))
                .andExpect(jsonPath("$.contents[0].role").value("user"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("안녕"))
                .andExpect(jsonPath("$.contents[1].role").value("model"))
                .andExpect(jsonPath("$.contents[2].parts[0].text").value("금리가 뭐야?"))
                .andExpect(jsonPath("$.generationConfig.maxOutputTokens").value(400))
                .andExpect(jsonPath("$.generationConfig.thinkingConfig.thinkingLevel").value("MINIMAL"))
                .andRespond(withSuccess("""
                        {"candidates":[{"content":{"role":"model","parts":[
                          {"text":"생각 중…","thought":true},
                          {"text":"금리는 돈을 빌릴 때 내는 "},{"text":"이자의 비율이에요."}]},
                          "finishReason":"STOP"}]}
                        """, MediaType.APPLICATION_JSON));

        String answer = client.generate(List.of(ChatTurn.user("안녕"), ChatTurn.model("안녕하세요!")), "금리가 뭐야?", false);

        server.verify();
        assertThat(answer).isEqualTo("금리는 돈을 빌릴 때 내는 이자의 비율이에요.");
    }

    @Test
    void 모델이_생각_설정을_거부하면_다음_방식으로_바꾸고_기억한다() {
        // Gemini 2.5 계열: thinkingLevel 은 거부, thinkingBudget 은 허용
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.generationConfig.thinkingConfig.thinkingLevel").value("MINIMAL"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":400,\"message\":\"Thinking level is not supported for this model.\",\"status\":\"INVALID_ARGUMENT\"}}"));
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.generationConfig.thinkingConfig.thinkingBudget").value(0))
                .andRespond(withSuccess("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"첫 답\"}]}}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.generationConfig.thinkingConfig.thinkingBudget").value(0))
                .andRespond(withSuccess("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"둘째 답\"}]}}]}", MediaType.APPLICATION_JSON));

        assertThat(client.generate(List.of(), "안녕", false)).isEqualTo("첫 답");
        assertThat(client.generate(List.of(), "또 안녕", false)).isEqualTo("둘째 답"); // 바로 두 번째 방식 사용
        server.verify();
        assertThat(client.thinkingSetting()).isEqualTo("{thinkingBudget=0}");
    }

    @Test
    void 생각_설정을_전혀_모르는_모델이면_설정_없이_보낸다() {
        for (String rejected : List.of("thinkingLevel", "thinkingBudget")) {
            server.expect(requestTo(URL))
                    .andExpect(jsonPath("$.generationConfig.thinkingConfig." + rejected).exists())
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                            .body("{\"error\":{\"code\":400,\"message\":\"Unknown name \\\"thinkingConfig\\\"\",\"status\":\"INVALID_ARGUMENT\"}}"));
        }
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.generationConfig.thinkingConfig").doesNotExist())
                .andRespond(withSuccess("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"답\"}]}}]}", MediaType.APPLICATION_JSON));

        assertThat(client.generate(List.of(), "안녕", false)).isEqualTo("답");
        server.verify();
        assertThat(client.thinkingSetting()).startsWith("설정 없음");
    }

    @Test
    void 오류_종류를_구분한다() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":{\"code\":429,\"status\":\"RESOURCE_EXHAUSTED\"}}"));
        assertThatThrownBy(() -> client.generate(List.of(), "질문", false))
                .isInstanceOfSatisfying(GeminiException.class, e -> assertThat(e.reason()).isEqualTo(GeminiException.Reason.RATE_LIMITED));

        server.reset();
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":{\"code\":400,\"message\":\"API key not valid.\",\"status\":\"INVALID_ARGUMENT\",\"details\":[{\"reason\":\"API_KEY_INVALID\"}]}}"));
        assertThatThrownBy(() -> client.generate(List.of(), "질문", false))
                .isInstanceOfSatisfying(GeminiException.class, e -> {
                    assertThat(e.reason()).isEqualTo(GeminiException.Reason.BAD_KEY);
                    assertThat(e.getMessage()).contains("secrets.properties").doesNotContain("test-key");
                });

        server.reset();
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"error\":{\"code\":404}}"));
        assertThatThrownBy(() -> client.generate(List.of(), "질문", false))
                .isInstanceOfSatisfying(GeminiException.class, e -> assertThat(e.reason()).isEqualTo(GeminiException.Reason.MODEL_NOT_FOUND));

        server.reset();
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.generate(List.of(), "질문", false))
                .isInstanceOfSatisfying(GeminiException.class, e -> assertThat(e.reason()).isEqualTo(GeminiException.Reason.BLOCKED));
    }

    @Test
    void 키와_모델을_확인한다() {
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("x-goog-api-key", "test-key"))
                .andRespond(withSuccess("{\"name\":\"models/gemini-flash-lite-latest\",\"displayName\":\"Gemini Flash-Lite Latest\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.checkModel()).isEqualTo("Gemini Flash-Lite Latest");
    }

    public static GeminiProperties properties(String key, String model) {
        return new GeminiProperties(key, model, "https://generativelanguage.googleapis.com",
                Duration.ofMillis(3500), Duration.ofSeconds(45), 400, true, 0.7, 3, Duration.ofMinutes(30), "짧게 답해");
    }
}
