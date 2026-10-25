package com.example.newsbot.chatbot;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 스킬 처리 중 어떤 예외가 나더라도 카카오에는 정상(200) 말풍선으로 답한다.
 * 오류 응답(5xx)을 돌려주면 카카오가 "스킬 서버 에러 응답 (1002)" 로 기록하고 친구는 아무 답도 못 받는다.
 */
@RestControllerAdvice(assignableTypes = {KakaoSkillController.class, ChatSkillController.class})
@ConditionalOnProperty(name = "newsbot.chatbot.enabled", havingValue = "true", matchIfMissing = true)
public class SkillErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(SkillErrorHandler.class);
    static final String FALLBACK_TEXT = "잠깐 문제가 생겼어요. 잠시 후 다시 시도해 주세요.";

    private final NewsSkillService news;
    private final RecentErrors recentErrors;

    public SkillErrorHandler(NewsSkillService news, RecentErrors recentErrors) {
        this.news = news;
        this.recentErrors = recentErrors;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handle(Exception e, HttpServletRequest request) {
        String where = request.getMethod() + " " + request.getRequestURI();
        recentErrors.add(where, e);
        log.error("스킬 처리 중 오류 ({}) — 친구에게는 안내 문구로 답했습니다", where, e);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(KakaoSkillResponses.text(FALLBACK_TEXT, news.categoryNames()));
    }
}
