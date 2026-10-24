package com.example.newsbot.chatbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import com.example.newsbot.ai.ChatMemory;
import com.example.newsbot.ai.GeminiClient;
import com.example.newsbot.ai.GeminiClientTest;
import com.example.newsbot.ai.GeminiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class ChatSkillServiceTest {

    private static final String CALLBACK = "https://bot-api.kakao.com/v1/callback/abc";

    private final JsonMapper json = JsonMapper.builder().build();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"), ZoneOffset.UTC);
    private MockRestServiceServer server;

    private ChatSkillService service(GeminiProperties gemini) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient rc = builder.build();
        RestClient geminiRc = builder.clone().baseUrl("https://generativelanguage.googleapis.com").build();
        GeminiClient client = new GeminiClient(gemini, json, geminiRc, geminiRc);
        NewsSkillService news = new NewsSkillService(NewsSkillServiceTest.properties(), NewsSkillServiceTest.chatbot(), clock, rc);
        // 테스트에서는 콜백을 바로(같은 스레드에서) 실행한다
        return new ChatSkillService(gemini, client, new ChatMemory(3, Duration.ofMinutes(30)), news, Runnable::run, rc, json, clock);
    }

    @Test
    void 제미나이로_답하고_다음_질문에는_이전_대화를_함께_보낸다() throws IOException {
        ChatSkillService service = service(GeminiClientTest.properties("k", "gemini-flash-lite-latest"));
        server.expect(requestTo(GeminiClientTest.URL))
                .andExpect(jsonPath("$.contents.length()").value(1))
                .andRespond(withSuccess(geminiAnswer("금리는 이자의 비율이에요."), MediaType.APPLICATION_JSON));
        server.expect(requestTo(GeminiClientTest.URL))
                .andExpect(jsonPath("$.contents.length()").value(3))
                .andExpect(jsonPath("$.contents[1].parts[0].text").value("금리는 이자의 비율이에요."))
                .andRespond(withSuccess(geminiAnswer("오르면 대출 이자가 늘어요."), MediaType.APPLICATION_JSON));

        JsonNode first = json.valueToTree(service.reply(payload("금리가 뭐야?", null)));
        JsonNode second = json.valueToTree(service.reply(payload("오르면 어떻게 돼?", null)));

        server.verify();
        assertThat(first.at("/template/outputs/0/simpleText/text").asString()).isEqualTo("금리는 이자의 비율이에요.");
        assertThat(second.at("/template/outputs/0/simpleText/text").asString()).isEqualTo("오르면 대출 이자가 늘어요.");
        assertThat(second.at("/template/quickReplies")).hasSize(5);
    }

    @Test
    void 뉴스를_찾는_말이면_제미나이_대신_뉴스_카드로_답한다() throws IOException {
        ChatSkillService service = service(GeminiClientTest.properties("k", "gemini-flash-lite-latest"));
        server.expect(requestTo(NewsSkillServiceTest.POLITICS_RSS))
                .andRespond(withSuccess(NewsSkillServiceTest.rss(5), MediaType.APPLICATION_XML));

        JsonNode r = json.valueToTree(service.reply(payload("오늘 정치 뉴스 알려줘", null)));

        server.verify();
        assertThat(r.at("/template/outputs/0/listCard/header/title").asString()).isEqualTo("📰 정치 뉴스");
    }

    @Test
    void 콜백이_켜져_있으면_먼저_기다려_달라고_하고_답은_콜백_주소로_보낸다() throws IOException {
        ChatSkillService service = service(GeminiClientTest.properties("k", "gemini-flash-lite-latest"));
        server.expect(requestTo(GeminiClientTest.URL))
                .andRespond(withSuccess(geminiAnswer("긴 답변입니다."), MediaType.APPLICATION_JSON));
        server.expect(requestTo(CALLBACK))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.version").value("2.0"))
                .andExpect(jsonPath("$.template.outputs[0].simpleText.text").value("긴 답변입니다."))
                .andRespond(withSuccess("{\"taskId\":\"t1\",\"status\":\"SUCCESS\"}", MediaType.APPLICATION_JSON));

        Map<String, Object> reply = service.reply(payload("세계 경제 전망을 자세히 설명해 줘", CALLBACK));

        server.verify();
        assertThat(reply).containsEntry("useCallback", true);
        assertThat(((Map<?, ?>) reply.get("data")).get("text")).isEqualTo(ChatSkillService.WAITING_TEXT);
    }

    @Test
    void 카카오가_아닌_콜백_주소는_무시하고_바로_답한다() throws IOException {
        assertThat(ChatSkillService.trustedCallback(CALLBACK)).isTrue();
        assertThat(ChatSkillService.trustedCallback("http://bot-api.kakao.com/x")).isFalse();
        assertThat(ChatSkillService.trustedCallback("https://kakao.com.evil.example/x")).isFalse();
        assertThat(ChatSkillService.trustedCallback("https://169.254.169.254/latest")).isFalse();

        ChatSkillService service = service(GeminiClientTest.properties("k", "gemini-flash-lite-latest"));
        server.expect(requestTo(GeminiClientTest.URL)).andRespond(withSuccess(geminiAnswer("바로 답"), MediaType.APPLICATION_JSON));

        JsonNode r = json.valueToTree(service.reply(payload("안녕", "https://evil.example/steal")));

        server.verify();
        assertThat(r.at("/template/outputs/0/simpleText/text").asString()).isEqualTo("바로 답");
    }

    @Test
    void 제미나이_오류와_키_미설정과_대화_초기화를_안내한다() throws IOException {
        ChatSkillService service = service(GeminiClientTest.properties("k", "gemini-flash-lite-latest"));
        server.expect(requestTo(GeminiClientTest.URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        JsonNode limited = json.valueToTree(service.reply(payload("안녕", null)));
        assertThat(limited.at("/template/outputs/0/simpleText/text").asString()).contains("AI 사용량이 많아");

        JsonNode reset = json.valueToTree(service.reply(payload("새 대화", null)));
        assertThat(reset.at("/template/outputs/0/simpleText/text").asString()).isEqualTo(ChatSkillService.RESET_TEXT);

        ChatSkillService noKey = service(GeminiClientTest.properties("", "gemini-flash-lite-latest"));
        JsonNode r = json.valueToTree(noKey.reply(payload("안녕", null)));
        assertThat(r.at("/template/outputs/0/simpleText/text").asString()).isEqualTo(ChatSkillService.NOT_CONFIGURED_TEXT);
    }

    @Test
    void 긴_답변은_카카오_글자_수_제한에_맞춰_자른다() throws IOException {
        ChatSkillService service = service(GeminiClientTest.properties("k", "gemini-flash-lite-latest"));
        server.expect(requestTo(GeminiClientTest.URL)).andRespond(withSuccess(geminiAnswer("가".repeat(1500)), MediaType.APPLICATION_JSON));

        String text = json.valueToTree(service.reply(payload("길게", null))).at("/template/outputs/0/simpleText/text").asString();

        assertThat(text.codePointCount(0, text.length())).isEqualTo(1000);
    }

    private ObjectNode payload(String utterance, String callbackUrl) throws IOException {
        ObjectNode p;
        try (var in = getClass().getResourceAsStream("/fixtures/skill-request.json")) {
            p = (ObjectNode) json.readTree(in);
        }
        ObjectNode userRequest = (ObjectNode) p.get("userRequest");
        userRequest.put("utterance", utterance);
        ((ObjectNode) userRequest.get("block")).put("name", "폴백 블록");
        ((ObjectNode) p.get("intent")).put("name", "폴백 블록");
        if (callbackUrl != null) {
            userRequest.put("callbackUrl", callbackUrl);
        }
        return p;
    }

    private static String geminiAnswer(String text) {
        return "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":" + JsonMapper.builder().build().writeValueAsString(text) + "}]},\"finishReason\":\"STOP\"}]}";
    }
}
