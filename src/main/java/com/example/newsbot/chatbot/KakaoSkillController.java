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
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

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
    private final JsonMapper jsonMapper;
    private final RecentErrors recentErrors;

    public KakaoSkillController(NewsSkillService service, SkillTokenGuard guard, JsonMapper jsonMapper,
                                RecentErrors recentErrors) {
        this.service = service;
        this.guard = guard;
        this.jsonMapper = jsonMapper;
        this.recentErrors = recentErrors;
    }

    @PostMapping(path = "/news", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> news(@RequestBody(required = false) JsonNode payload,
                                                    @RequestParam(required = false) String token) {
        if (!guard.allows(token)) {
            log.warn("스킬 토큰이 맞지 않아 거부했습니다. 챗봇 관리자센터의 스킬 URL 끝에 ?token=값 이 있는지 확인하세요.");
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

    /**
     * 브라우저 확인용. ?say=정치 를 붙이면 카카오에 보낼 응답 JSON 을 그대로 보여 준다.
     */
    @GetMapping("/news")
    public ResponseEntity<?> health(@RequestParam(required = false) String say) {
        if (say != null && !say.isBlank()) {
            ObjectNode payload = jsonMapper.createObjectNode();
            payload.putObject("userRequest").put("utterance", say);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(service.reply(payload));
        }
        return ResponseEntity.ok().contentType(TEXT_UTF8)
                .body("뉴스 챗봇 스킬 서버가 동작 중입니다.\n응답 미리보기: 주소 뒤에 ?say=정치 를 붙여 보세요.\n" + recentErrors.summary());
    }
}
