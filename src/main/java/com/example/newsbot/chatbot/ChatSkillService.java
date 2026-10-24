package com.example.newsbot.chatbot;

import java.net.URI;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;

import com.example.newsbot.ai.ChatMemory;
import com.example.newsbot.ai.ChatTurn;
import com.example.newsbot.ai.GeminiClient;
import com.example.newsbot.ai.GeminiException;
import com.example.newsbot.ai.GeminiProperties;
import com.example.newsbot.delivery.MessageText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 폴백 블록(챗봇이 못 알아들은 말)을 제미나이가 대화로 받는다.
 * <ul>
 *   <li>블록에 콜백이 켜져 있으면: "생각하고 있어요"를 먼저 보내고, 답이 나오면 callbackUrl 로 보낸다.</li>
 *   <li>콜백이 없으면: 카카오 5초 제한 안에 바로 답한다. 늦으면 다시 물어 달라고 안내한다.</li>
 *   <li>"정치 뉴스" 처럼 뉴스를 찾는 말이면 제미나이 대신 뉴스 카드로 답한다.</li>
 * </ul>
 */
public class ChatSkillService {

    private static final Logger log = LoggerFactory.getLogger(ChatSkillService.class);

    static final int SIMPLE_TEXT_MAX = 1000;
    static final String WAITING_TEXT = "생각하고 있어요. 잠시만 기다려 주세요 🙂";
    static final String NOT_CONFIGURED_TEXT = "AI 대화 기능이 아직 준비되지 않았어요. 아래 버튼으로 뉴스를 볼 수 있어요.";
    static final String RESET_TEXT = "대화를 새로 시작할게요. 무엇이든 물어보세요!";
    private static final List<String> RESET_WORDS = List.of("새대화", "대화초기화", "처음부터");

    private final GeminiProperties gemini;
    private final GeminiClient client;
    private final ChatMemory memory;
    private final NewsSkillService news;
    private final Executor executor;
    private final RestClient callbackClient;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public ChatSkillService(GeminiProperties gemini, GeminiClient client, ChatMemory memory, NewsSkillService news,
                            Executor executor, RestClient callbackClient, JsonMapper jsonMapper, Clock clock) {
        this.gemini = gemini;
        this.client = client;
        this.memory = memory;
        this.news = news;
        this.executor = executor;
        this.callbackClient = callbackClient;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    public Map<String, Object> reply(JsonNode payload) {
        List<String> categories = news.categoryNames();
        String utterance = payload == null ? "" : payload.path("userRequest").path("utterance").asString("").strip();
        String userId = payload == null ? "anonymous" : payload.path("userRequest").path("user").path("id").asString("anonymous");

        if (utterance.isEmpty()) {
            return KakaoSkillResponses.text(NewsSkillService.MENU_TEXT, categories);
        }
        String compact = utterance.replaceAll("\\s+", "");
        if (RESET_WORDS.contains(compact)) {
            memory.forget(userId);
            return KakaoSkillResponses.text(RESET_TEXT, categories);
        }
        if (looksLikeNewsRequest(compact) && news.resolve(payload).isPresent()) {
            return news.reply(payload);
        }
        if (!gemini.configured()) {
            log.warn("GEMINI_API_KEY 가 없어 AI 대화를 할 수 없습니다. secrets.properties 를 확인하세요.");
            return KakaoSkillResponses.text(NOT_CONFIGURED_TEXT, categories);
        }

        String callbackUrl = payload.path("userRequest").path("callbackUrl").asString("");
        if (!callbackUrl.isEmpty() && trustedCallback(callbackUrl)) {
            executor.execute(() -> sendCallback(callbackUrl, userId, utterance, categories));
            Map<String, Object> waiting = new LinkedHashMap<>();
            waiting.put("version", "2.0");
            waiting.put("useCallback", true);
            waiting.put("data", Map.of("text", WAITING_TEXT));
            return waiting;
        }
        return KakaoSkillResponses.text(answer(userId, utterance, false), categories);
    }

    String answer(String userId, String question, boolean longWait) {
        long started = System.nanoTime();
        try {
            List<ChatTurn> history = memory.history(userId, clock.instant());
            String answer = client.generate(history, question, longWait);
            memory.remember(userId, question, answer, clock.instant());
            log.info("제미나이 답변 완료 ({}ms, 이전 대화 {}개)", (System.nanoTime() - started) / 1_000_000, history.size());
            return MessageText.truncate(answer, SIMPLE_TEXT_MAX);
        } catch (GeminiException e) {
            log.warn("제미나이 답변 실패 ({}ms): {}", (System.nanoTime() - started) / 1_000_000, e.getMessage());
            return e.userMessage();
        }
    }

    private void sendCallback(String callbackUrl, String userId, String question, List<String> categories) {
        String answer = answer(userId, question, true);
        try {
            byte[] body = jsonMapper.writeValueAsBytes(KakaoSkillResponses.text(answer, categories));
            String result = callbackClient.post()
                    .uri(URI.create(callbackUrl))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            log.info("카카오 콜백 전송 완료: {}", result);
        } catch (RuntimeException e) {
            log.warn("카카오 콜백 전송 실패: {}", e.getMessage());
        }
    }

    /** 다른 곳으로 답을 보내도록 조작된 요청을 막는다: 카카오 주소(https)만 허용 */
    static boolean trustedCallback(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            boolean local = host.equals("localhost") || host.equals("127.0.0.1");
            boolean kakao = host.equals("kakao.com") || host.endsWith(".kakao.com");
            return local || (kakao && "https".equals(uri.getScheme()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean looksLikeNewsRequest(String compact) {
        return compact.contains("뉴스") || compact.contains("기사") || compact.contains("헤드라인");
    }
}
