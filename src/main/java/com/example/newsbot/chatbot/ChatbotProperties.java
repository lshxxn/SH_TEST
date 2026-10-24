package com.example.newsbot.chatbot;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 카카오 챗봇 스킬 서버 설정 ({@code newsbot.chatbot.*}).
 */
@ConfigurationProperties(prefix = "newsbot.chatbot")
public record ChatbotProperties(
        @DefaultValue("true") boolean enabled,
        /** 한 번에 보여 줄 기사 수. 카카오 리스트 카드는 최대 5개 */
        @DefaultValue("5") int maxItems,
        /** 같은 분야를 이 시간 안에 다시 물으면 방금 가져온 결과를 쓴다 */
        @DefaultValue("5m") Duration cacheTtl,
        /** 카카오는 5초 안에 답을 받아야 하므로 뉴스 서버 연결·응답 시간을 짧게 잡는다 */
        @DefaultValue("1500ms") Duration connectTimeout,
        @DefaultValue("2500ms") Duration readTimeout,
        /** 설정하면 스킬 URL 에 ?token=값 이 있어야만 응답한다 */
        String skillToken) {
}
