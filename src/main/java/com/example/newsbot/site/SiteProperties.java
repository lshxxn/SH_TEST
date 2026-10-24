package com.example.newsbot.site;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 뉴스 페이지 생성 설정 ({@code newsbot.site.*}).
 */
@ConfigurationProperties(prefix = "newsbot.site")
public record SiteProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("_site") Path outputDir,
        @DefaultValue("오늘의 뉴스 클리핑") String title,
        /** 페이지가 올라갈 주소. 공유 버튼과 챗봇 응답에 쓴다. */
        String pageUrl,
        /** 카카오톡 채널 홈 주소 (예: https://pf.kakao.com/_xxxx). 있으면 "채널 추가" 버튼을 보여 준다. */
        String channelUrl) {
}
