package com.example.newsbot.delivery.kakao;

import java.nio.file.Path;

final class KakaoTestSupport {

    private KakaoTestSupport() {
    }

    static KakaoProperties properties(Path tokenFile, String clientSecret) {
        return new KakaoProperties(true, "rest-key", clientSecret, "http://localhost:8080/kakao/callback",
                tokenFile, "https://kauth.kakao.com", "https://kapi.kakao.com");
    }

    static String tokenJson(String accessToken, String refreshToken) {
        String refresh = refreshToken == null ? ""
                : ",\"refresh_token\":\"" + refreshToken + "\",\"refresh_token_expires_in\":5183999";
        return "{\"token_type\":\"bearer\",\"access_token\":\"" + accessToken + "\",\"expires_in\":21599"
                + refresh + ",\"scope\":\"talk_message\"}";
    }
}
