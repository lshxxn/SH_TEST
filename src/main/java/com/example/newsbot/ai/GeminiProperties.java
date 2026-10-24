package com.example.newsbot.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 제미나이(Gemini) API 설정 ({@code newsbot.gemini.*}).
 */
@ConfigurationProperties(prefix = "newsbot.gemini")
public record GeminiProperties(
        /** Google AI Studio 에서 발급한 API 키. secrets.properties 의 GEMINI_API_KEY */
        String apiKey,
        /** 기본값은 구글이 항상 최신 경량 모델로 연결해 주는 이름 */
        @DefaultValue("gemini-flash-lite-latest") String model,
        @DefaultValue("https://generativelanguage.googleapis.com") String baseUrl,
        /** 콜백이 없을 때: 카카오 5초 제한 안에 답하도록 이 시간에서 끊는다 */
        @DefaultValue("3500ms") Duration timeout,
        /** 콜백이 있을 때: 카카오 콜백 주소 유효 시간보다 짧게 */
        @DefaultValue("45s") Duration callbackTimeout,
        @DefaultValue("400") int maxOutputTokens,
        @DefaultValue("0.7") double temperature,
        /** 친구별로 기억할 최근 대화 수 (질문+답 한 쌍이 1) */
        @DefaultValue("3") int historyTurns,
        /** 이 시간 동안 말이 없으면 대화 기억을 지운다 */
        @DefaultValue("30m") Duration historyTtl,
        String systemPrompt) {

    public GeminiProperties {
        apiKey = clean(apiKey);
        model = clean(model);
    }

    /** 복사·붙여넣기 실수로 붙은 앞뒤 공백과 따옴표를 지운다. */
    static String clean(String value) {
        if (value == null) {
            return null;
        }
        String v = value.strip();
        while (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
            v = v.substring(1, v.length() - 1).strip();
        }
        return v;
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** "models/gemini-..." 처럼 복사해 와도 동작하도록 */
    public String modelName() {
        String m = model == null ? "" : model.trim();
        return m.startsWith("models/") ? m.substring("models/".length()) : m;
    }
}
