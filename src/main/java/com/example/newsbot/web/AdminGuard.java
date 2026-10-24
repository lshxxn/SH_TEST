package com.example.newsbot.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import com.example.newsbot.config.NewsBotProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

/**
 * 관리용 엔드포인트를 NEWSBOT_ADMIN_TOKEN 으로 보호한다.
 * 토큰이 설정되지 않았으면 관리용 엔드포인트는 모두 막힌다.
 */
@Component
public class AdminGuard {

    private final String adminToken;

    public AdminGuard(NewsBotProperties properties) {
        this.adminToken = properties.adminToken();
    }

    public void check(String provided) {
        if (!StringUtils.hasText(adminToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "NEWSBOT_ADMIN_TOKEN 환경 변수를 설정해야 관리 기능을 쓸 수 있습니다.");
        }
        if (provided == null || !MessageDigest.isEqual(
                adminToken.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 토큰이 올바르지 않습니다.");
        }
    }
}
