package com.example.newsbot.delivery.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POST https://kauth.kakao.com/oauth/token 응답.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoTokenResponse(
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("refresh_token_expires_in") Long refreshTokenExpiresIn,
        @JsonProperty("scope") String scope) {
}
