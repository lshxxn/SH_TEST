package com.example.newsbot.delivery.kakao;

import java.time.Instant;

/**
 * 파일에 저장하는 카카오 토큰.
 */
public record KakaoToken(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        String scope) {

    static KakaoToken issued(KakaoTokenResponse r, Instant now) {
        return new KakaoToken(
                r.accessToken(),
                now.plusSeconds(r.expiresIn()),
                r.refreshToken(),
                r.refreshTokenExpiresIn() == null ? null : now.plusSeconds(r.refreshTokenExpiresIn()),
                r.scope());
    }

    /**
     * 갱신 응답을 반영한다. 카카오는 리프레시 토큰 만료가 1개월 미만 남았을 때만
     * 새 리프레시 토큰을 내려주므로, 응답에 없으면 기존 값을 유지한다.
     */
    KakaoToken refreshedWith(KakaoTokenResponse r, Instant now) {
        boolean rotated = r.refreshToken() != null && !r.refreshToken().isBlank();
        return new KakaoToken(
                r.accessToken(),
                now.plusSeconds(r.expiresIn()),
                rotated ? r.refreshToken() : refreshToken,
                rotated && r.refreshTokenExpiresIn() != null
                        ? now.plusSeconds(r.refreshTokenExpiresIn())
                        : refreshTokenExpiresAt,
                r.scope() != null ? r.scope() : scope);
    }
}
