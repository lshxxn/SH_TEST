package com.example.newsbot.chatbot;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.example.newsbot.ai.GeminiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * AI 대화 스킬 주소: POST /kakao/skill/chat
 * 챗봇 관리자센터에서 폴백 블록에 이 스킬을 연결한다.
 */
@RestController
@RequestMapping("/kakao/skill")
@ConditionalOnProperty(name = "newsbot.chatbot.enabled", havingValue = "true", matchIfMissing = true)
public class ChatSkillController {

    private static final Logger log = LoggerFactory.getLogger(ChatSkillController.class);
    private static final MediaType TEXT_UTF8 = new MediaType("text", "plain", StandardCharsets.UTF_8);

    private final ChatSkillService service;
    private final SkillTokenGuard guard;
    private final GeminiStatus geminiStatus;
    private final RecentErrors recentErrors;

    public ChatSkillController(ChatSkillService service, SkillTokenGuard guard, GeminiStatus geminiStatus,
                               RecentErrors recentErrors) {
        this.service = service;
        this.guard = guard;
        this.geminiStatus = geminiStatus;
        this.recentErrors = recentErrors;
    }

    @PostMapping(path = "/chat", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> chat(@RequestBody(required = false) JsonNode payload,
                                                    @RequestParam(required = false) String token) {
        if (!guard.allows(token)) {
            log.warn("스킬 토큰이 맞지 않아 거부했습니다. 챗봇 관리자센터의 스킬 URL 끝에 ?token=값 이 있는지 확인하세요.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        long started = System.nanoTime();
        Map<String, Object> reply = service.reply(payload);
        log.info("AI 대화 요청 처리: {}ms{}", (System.nanoTime() - started) / 1_000_000,
                Boolean.TRUE.equals(reply.get("useCallback")) ? " (콜백으로 이어서 답함)" : "");
        return ResponseEntity.ok(reply);
    }

    @GetMapping("/chat")
    public ResponseEntity<String> health() {
        GeminiStatus.Result status = geminiStatus.current();
        return ResponseEntity.ok().contentType(TEXT_UTF8)
                .body("AI 대화 스킬 서버가 동작 중입니다.\n제미나이 상태: " + status.message() + "\n" + recentErrors.summary());
    }
}
