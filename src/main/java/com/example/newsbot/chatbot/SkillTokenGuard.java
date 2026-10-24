package com.example.newsbot.chatbot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.util.StringUtils;

/**
 * NEWSBOT_SKILL_TOKEN 을 정했다면 스킬 URL 의 ?token= 값이 같아야 응답한다.
 */
public class SkillTokenGuard {

    private final String expected;

    public SkillTokenGuard(String expected) {
        this.expected = expected;
    }

    public boolean allows(String token) {
        if (!StringUtils.hasText(expected)) {
            return true;
        }
        return token != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
    }
}
