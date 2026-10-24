package com.example.newsbot.config;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * application.yml 의 {@code newsbot.*} 설정.
 */
@ConfigurationProperties(prefix = "newsbot")
public record NewsBotProperties(
        @DefaultValue Schedule schedule,
        @DefaultValue News news,
        @DefaultValue Http http,
        /** 수동 발송·카카오 로그인 엔드포인트 보호용 토큰. 비어 있으면 해당 엔드포인트는 막힌다. */
        String adminToken) {

    public record Schedule(
            @DefaultValue("0 0 9 * * *") String cron,
            @DefaultValue("Asia/Seoul") ZoneId zone) {
    }

    public record News(
            /** 섹션마다 보낼 최대 기사 수 */
            @DefaultValue("5") int maxItemsPerSection,
            /** 이 시간보다 오래된 기사는 제외 (발행 시각이 없는 기사는 포함) */
            @DefaultValue("24h") Duration lookback,
            /** 메시지 상단·"더보기" 링크 */
            @DefaultValue("https://news.google.com/home?hl=ko&gl=KR&ceid=KR:ko") String moreLink,
            @DefaultValue List<Section> sections) {
    }

    /**
     * 뉴스 섹션 하나. {@code rss} 또는 {@code keyword} 중 하나를 지정한다.
     * keyword 를 쓰면 구글 뉴스 검색 RSS(최근 1일)로 변환된다.
     */
    public record Section(String name, String rss, String keyword) {

        public URI feedUri() {
            if (StringUtils.hasText(rss)) {
                return URI.create(rss.trim());
            }
            if (StringUtils.hasText(keyword)) {
                String q = URLEncoder.encode(keyword.trim() + " when:1d", StandardCharsets.UTF_8);
                return URI.create("https://news.google.com/rss/search?q=" + q + "&hl=ko&gl=KR&ceid=KR:ko");
            }
            throw new IllegalStateException("섹션 '" + name + "' 에 rss 또는 keyword 를 지정하세요.");
        }

        public String displayName() {
            if (StringUtils.hasText(name)) {
                return name.trim();
            }
            return StringUtils.hasText(keyword) ? keyword.trim() : "뉴스";
        }
    }

    public record Http(
            @DefaultValue("5s") Duration connectTimeout,
            @DefaultValue("15s") Duration readTimeout,
            @DefaultValue("Mozilla/5.0 (compatible; kakao-news-bot/0.1)") String userAgent) {
    }
}
