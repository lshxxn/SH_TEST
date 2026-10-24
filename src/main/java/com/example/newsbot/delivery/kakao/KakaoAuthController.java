package com.example.newsbot.delivery.kakao;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.example.newsbot.web.AdminGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 최초 1회 카카오 계정을 연결하는 엔드포인트.
 * <ol>
 *   <li>브라우저에서 GET /kakao/login?token=관리자토큰</li>
 *   <li>카카오 로그인·동의 후 GET /kakao/callback 으로 돌아오면 토큰을 파일에 저장</li>
 * </ol>
 * state 값으로 다른 사람이 자기 계정을 끼워 넣는 것(CSRF)을 막는다.
 */
@RestController
@RequestMapping("/kakao")
@ConditionalOnProperty(name = "newsbot.kakao.enabled", havingValue = "true")
public class KakaoAuthController {

    private static final Logger log = LoggerFactory.getLogger(KakaoAuthController.class);
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final MediaType TEXT_UTF8 = new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8);

    private final KakaoOAuthClient oauthClient;
    private final KakaoTokenService tokenService;
    private final AdminGuard adminGuard;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Instant> pendingStates = new ConcurrentHashMap<>();

    public KakaoAuthController(KakaoOAuthClient oauthClient, KakaoTokenService tokenService,
                               AdminGuard adminGuard, Clock clock) {
        this.oauthClient = oauthClient;
        this.tokenService = tokenService;
        this.adminGuard = adminGuard;
        this.clock = clock;
    }

    @GetMapping("/login")
    public ResponseEntity<Void> login(@RequestParam(name = "token", required = false) String token) {
        adminGuard.check(token);
        Instant now = clock.instant();
        pendingStates.values().removeIf(expiry -> expiry.isBefore(now));
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String state = HexFormat.of().formatHex(bytes);
        pendingStates.put(state, now.plus(STATE_TTL));
        URI authorize = oauthClient.authorizeUri(state);
        return ResponseEntity.status(HttpStatus.FOUND).location(authorize).build();
    }

    @GetMapping("/callback")
    public ResponseEntity<String> callback(@RequestParam(required = false) String code,
                                           @RequestParam(required = false) String state,
                                           @RequestParam(required = false) String error,
                                           @RequestParam(name = "error_description", required = false) String errorDescription) {
        if (error != null) {
            return text(HttpStatus.BAD_REQUEST, "카카오 로그인이 취소되었거나 실패했습니다: " + error
                    + (errorDescription != null ? " - " + errorDescription : ""));
        }
        Instant expiry = state == null ? null : pendingStates.remove(state);
        if (expiry == null || expiry.isBefore(clock.instant())) {
            return text(HttpStatus.BAD_REQUEST, "유효하지 않은 state 입니다. /kakao/login 부터 다시 시작하세요.");
        }
        if (code == null || code.isBlank()) {
            return text(HttpStatus.BAD_REQUEST, "인가 코드(code)가 없습니다.");
        }
        try {
            tokenService.issue(code);
        } catch (RuntimeException e) {
            log.error("카카오 토큰 발급 실패", e);
            return text(HttpStatus.BAD_GATEWAY, "토큰 발급 실패: " + e.getMessage());
        }
        return text(HttpStatus.OK, "카카오 계정 연결 완료! 이제 매일 아침 '나와의 채팅'으로 뉴스가 옵니다. 이 창은 닫아도 됩니다.");
    }

    @GetMapping("/status")
    public Map<String, Object> status(@RequestParam(name = "token", required = false) String token) {
        adminGuard.check(token);
        Map<String, Object> status = new LinkedHashMap<>();
        tokenService.current().ifPresentOrElse(t -> {
            status.put("linked", true);
            status.put("scope", t.scope());
            status.put("accessTokenExpiresAt", t.accessTokenExpiresAt());
            status.put("refreshTokenExpiresAt", t.refreshTokenExpiresAt());
        }, () -> status.put("linked", false));
        return status;
    }

    private static ResponseEntity<String> text(HttpStatus status, String body) {
        return ResponseEntity.status(status).contentType(TEXT_UTF8).body(body);
    }
}
