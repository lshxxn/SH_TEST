package com.example.newsbot.delivery.kakao;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 카카오 "나에게 보내기" 설정 ({@code newsbot.kakao.*}).
 */
@ConfigurationProperties(prefix = "newsbot.kakao")
public record KakaoProperties(
        @DefaultValue("false") boolean enabled,
        /** 카카오디벨로퍼스 > 앱 > 플랫폼 키 > REST API 키 */
        String restApiKey,
        /** REST API 키의 클라이언트 시크릿 (사용 설정한 경우에만) */
        String clientSecret,
        @DefaultValue("http://localhost:8080/kakao/callback") String redirectUri,
        @DefaultValue("./data/kakao-token.json") Path tokenFile,
        @DefaultValue("https://kauth.kakao.com") String authBaseUrl,
        @DefaultValue("https://kapi.kakao.com") String apiBaseUrl) {
}
