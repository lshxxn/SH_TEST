package com.example.newsbot.delivery.kakao;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 토큰 발급·저장·자동 갱신.
 * 액세스 토큰은 몇 시간, 리프레시 토큰은 약 2개월 유효하다. 하루 한 번 발송할 때마다 갱신하므로
 * 리프레시 토큰도 만료 1개월 전부터 카카오가 자동으로 새로 내려준다.
 */
public class KakaoTokenService {

    private static final Logger log = LoggerFactory.getLogger(KakaoTokenService.class);
    private static final Duration EXPIRY_MARGIN = Duration.ofMinutes(5);

    private final KakaoOAuthClient oauthClient;
    private final KakaoTokenStore store;
    private final Clock clock;

    public KakaoTokenService(KakaoOAuthClient oauthClient, KakaoTokenStore store, Clock clock) {
        this.oauthClient = oauthClient;
        this.store = store;
        this.clock = clock;
    }

    /** 인가 코드로 토큰을 받아 저장한다 (최초 1회, /kakao/callback). */
    public synchronized KakaoToken issue(String authorizationCode) {
        KakaoToken token = KakaoToken.issued(oauthClient.issueToken(authorizationCode), clock.instant());
        store.save(token);
        log.info("카카오 토큰 저장 완료: {} (scope={})", store.file(), token.scope());
        if (token.scope() != null && !token.scope().contains(KakaoOAuthClient.TALK_MESSAGE_SCOPE)) {
            log.warn("talk_message 동의가 빠져 있습니다. 메시지 전송이 실패할 수 있습니다.");
        }
        return token;
    }

    /** 만료가 임박했으면 갱신한 뒤 유효한 액세스 토큰을 돌려준다. */
    public synchronized String validAccessToken() {
        KakaoToken token = current().orElseThrow(KakaoTokenService::notLinked);
        if (token.accessTokenExpiresAt() == null
                || token.accessTokenExpiresAt().isBefore(clock.instant().plus(EXPIRY_MARGIN))) {
            token = refresh(token);
        }
        return token.accessToken();
    }

    /** 401 을 받았을 때처럼 강제로 갱신한다. */
    public synchronized String forceRefresh() {
        return refresh(current().orElseThrow(KakaoTokenService::notLinked)).accessToken();
    }

    public Optional<KakaoToken> current() {
        return store.load();
    }

    private KakaoToken refresh(KakaoToken token) {
        Instant now = clock.instant();
        if (token.refreshTokenExpiresAt() != null && token.refreshTokenExpiresAt().isBefore(now)) {
            throw new IllegalStateException("리프레시 토큰이 만료됐습니다. /kakao/login 으로 다시 로그인하세요.");
        }
        KakaoToken refreshed = token.refreshedWith(oauthClient.refresh(token.refreshToken()), now);
        store.save(refreshed);
        if (!refreshed.refreshToken().equals(token.refreshToken())) {
            log.info("카카오 리프레시 토큰이 갱신됐습니다. 만료: {}", refreshed.refreshTokenExpiresAt());
        }
        return refreshed;
    }

    private static IllegalStateException notLinked() {
        return new IllegalStateException("카카오 계정이 연결되지 않았습니다. 브라우저에서 /kakao/login?token=<관리자 토큰> 을 여세요.");
    }
}
