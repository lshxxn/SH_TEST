package com.example.newsbot.chatbot;

import java.nio.charset.StandardCharsets;
import java.util.Map;

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
 * 카카오 i 오픈빌더 스킬 주소: POST /kakao/skill/news
 * 챗봇 관리자센터 [스킬] 에 "https://터널주소/kakao/skill/news" 로 등록한다.
 */
@RestController
@RequestMapping("/kakao/skill")
@ConditionalOnProperty(name = "newsbot.chatbot.enabled", havingValue = "true", matchIfMissing = true)
public class KakaoSkillController {

    private static final Logger log = LoggerFactory.getLogger(KakaoSkillController.class);
    private static final MediaType TEXT_UTF8 = new MediaType("text", "plain", StandardCharsets.UTF_8);

    private final NewsSkillService service;
    private final SkillTokenGuard guard;

    public KakaoSkillController(NewsSkillService service, SkillTokenGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @PostMapping(path = "/news", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> news(@RequestBody(required = false) JsonNode payload,
                                                    @RequestParam(required = false) String token) {
        if (!guard.allows(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        long started = System.nanoTime();
        Map<String, Object> reply = service.reply(payload);
        log.info("스킬 요청 처리: 발화='{}', 블록='{}', {}ms",
                payload == null ? null : payload.path("userRequest").path("utterance").asString(""),
                payload == null ? null : payload.path("userRequest").path("block").path("name").asString(""),
                (System.nanoTime() - started) / 1_000_000);
        return ResponseEntity.ok(reply);
    }

    /** 브라우저로 주소를 열었을 때 서버가 살아 있는지 확인용 */
    @GetMapping("/news")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok().contentType(TEXT_UTF8).body("뉴스 챗봇 스킬 서버가 동작 중입니다.");
    }
}
