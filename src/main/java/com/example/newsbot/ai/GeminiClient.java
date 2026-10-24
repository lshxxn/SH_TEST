package com.example.newsbot.ai;

import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 제미나이 REST API (generateContent) 호출.
 * POST {baseUrl}/v1beta/models/{model}:generateContent, 헤더 x-goog-api-key
 */
public class GeminiClient {

    private final GeminiProperties properties;
    private final JsonMapper jsonMapper;
    private final RestClient fast;
    private final RestClient slow;

    public GeminiClient(GeminiProperties properties, JsonMapper jsonMapper, RestClient fast, RestClient slow) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.fast = fast;
        this.slow = slow;
    }

    /** 응답 대기 시간이 다른 HTTP 클라이언트 두 개(바로 답하기용, 콜백용)를 만든다. */
    public static GeminiClient create(GeminiProperties properties, JsonMapper jsonMapper, RestClient.Builder builder) {
        return new GeminiClient(properties, jsonMapper,
                client(builder, properties, properties.timeout()),
                client(builder, properties, properties.callbackTimeout()));
    }

    private static RestClient client(RestClient.Builder builder, GeminiProperties properties, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return builder.clone().baseUrl(properties.baseUrl()).requestFactory(factory).build();
    }

    /**
     * @param history  이전 대화 (오래된 것부터)
     * @param question 이번 질문
     * @param longWait 콜백으로 답할 때 true (오래 기다려도 됨)
     */
    public String generate(List<ChatTurn> history, String question, boolean longWait) {
        List<Map<String, Object>> contents = new ArrayList<>();
        for (ChatTurn turn : history) {
            contents.add(content(turn.role(), turn.text()));
        }
        contents.add(content("user", question));

        Map<String, Object> body = new LinkedHashMap<>();
        if (properties.systemPrompt() != null && !properties.systemPrompt().isBlank()) {
            body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", properties.systemPrompt()))));
        }
        body.put("contents", contents);
        body.put("generationConfig", Map.of(
                "maxOutputTokens", properties.maxOutputTokens(),
                "temperature", properties.temperature()));

        JsonNode response = call(longWait ? slow : fast, jsonMapper.writeValueAsBytes(body));
        return extractText(response);
    }

    /** 키와 모델이 맞는지 확인한다 (GET /v1beta/models/{model}). 모델 표시 이름을 돌려준다. */
    public String checkModel() {
        try {
            JsonNode model = fast.get()
                    .uri("/v1beta/models/{model}", properties.modelName())
                    .header("x-goog-api-key", properties.apiKey())
                    .retrieve()
                    .onStatus(s -> s.isError(), (req, res) -> {
                        throw error(res.getStatusCode().value(),
                                new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8));
                    })
                    .body(JsonNode.class);
            return model == null ? properties.modelName() : model.path("displayName").asString(properties.modelName());
        } catch (ResourceAccessException e) {
            throw new GeminiException(GeminiException.Reason.TIMEOUT, "제미나이 서버에 연결하지 못했습니다: " + e.getMessage(), e);
        }
    }

    private JsonNode call(RestClient client, byte[] body) {
        try {
            return client.post()
                    .uri("/v1beta/models/{model}:generateContent", properties.modelName())
                    .header("x-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(s -> s.isError(), (req, res) -> {
                        throw error(res.getStatusCode().value(),
                                new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8));
                    })
                    .body(JsonNode.class);
        } catch (ResourceAccessException e) {
            boolean timeout = e.getCause() instanceof HttpTimeoutException
                    || (e.getMessage() != null && e.getMessage().contains("timed out"));
            throw new GeminiException(timeout ? GeminiException.Reason.TIMEOUT : GeminiException.Reason.OTHER,
                    "제미나이 호출 실패: " + e.getMessage(), e);
        }
    }

    static String extractText(JsonNode response) {
        if (response == null) {
            throw new GeminiException(GeminiException.Reason.OTHER, "빈 응답");
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : response.path("candidates").path(0).path("content").path("parts")) {
            if (!part.path("thought").asBoolean(false) && part.has("text")) {
                text.append(part.get("text").asString());
            }
        }
        if (text.isEmpty()) {
            String blockReason = response.path("promptFeedback").path("blockReason").asString("");
            String finish = response.path("candidates").path(0).path("finishReason").asString("");
            if (!blockReason.isEmpty() || "SAFETY".equals(finish) || "PROHIBITED_CONTENT".equals(finish)) {
                throw new GeminiException(GeminiException.Reason.BLOCKED, "안전 필터로 차단됨: " + blockReason + finish);
            }
            throw new GeminiException(GeminiException.Reason.OTHER, "답변 텍스트가 없습니다: finishReason=" + finish);
        }
        return text.toString().strip();
    }

    private static GeminiException error(int status, String body) {
        GeminiException.Reason reason;
        String hint;
        if (status == 429 || body.contains("RESOURCE_EXHAUSTED")) {
            reason = GeminiException.Reason.RATE_LIMITED;
            hint = "무료 사용량(분당/하루 요청 수)을 넘었습니다.";
        } else if (body.contains("API_KEY_INVALID") || status == 401 || status == 403) {
            reason = GeminiException.Reason.BAD_KEY;
            hint = "GEMINI_API_KEY 가 올바른지 secrets.properties 를 확인하세요.";
        } else if (status == 404) {
            reason = GeminiException.Reason.MODEL_NOT_FOUND;
            hint = "GEMINI_MODEL 이름을 Google AI Studio 의 모델 목록과 비교해 보세요.";
        } else {
            reason = GeminiException.Reason.OTHER;
            hint = "";
        }
        String shortBody = body.length() > 300 ? body.substring(0, 300) + "…" : body;
        return new GeminiException(reason, "제미나이 오류 (HTTP " + status + "): " + hint + " " + shortBody.replaceAll("\\s+", " "));
    }

    private static Map<String, Object> content(String role, String text) {
        return Map.of("role", role, "parts", List.of(Map.of("text", text)));
    }
}
