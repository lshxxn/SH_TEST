package com.example.newsbot.chatbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import com.example.newsbot.config.NewsBotProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class NewsSkillServiceTest {

    static final String POLITICS_RSS = "https://news.google.com/rss/search?q=%EC%A0%95%EC%B9%98+when%3A1d&hl=ko&gl=KR&ceid=KR:ko";
    static final String BUSINESS_RSS = "https://news.google.com/rss/headlines/section/topic/BUSINESS?hl=ko&gl=KR&ceid=KR:ko";

    private final JsonMapper json = JsonMapper.builder().build();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-24T00:00:00Z"));
    private MockRestServiceServer server;
    private NewsSkillService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new NewsSkillService(properties(), chatbot(), clock, builder.build());
    }

    @Test
    void 분야는_보낸_말과_블록_이름과_파라미터로_찾는다() throws IOException {
        assertThat(service.resolve(payload()).orElseThrow().displayName()).isEqualTo("정치");

        ObjectNode byBlock = payload();
        ((ObjectNode) byBlock.get("userRequest")).put("utterance", "돈 이야기 보여줘");
        ((ObjectNode) byBlock.at("/userRequest/block")).put("name", "경제");
        assertThat(service.resolve(byBlock).orElseThrow().displayName()).isEqualTo("경제");

        ObjectNode byParam = payload();
        ((ObjectNode) byParam.get("userRequest")).put("utterance", "아무말");
        ((ObjectNode) byParam.get("action").get("params")).put("category", "IT");
        assertThat(service.resolve(byParam).orElseThrow().displayName()).isEqualTo("IT·과학");

        ObjectNode welcome = payload();
        ((ObjectNode) welcome.get("userRequest")).put("utterance", "안녕하세요");
        ((ObjectNode) welcome.at("/userRequest/block")).put("name", "웰컴 블록");
        ((ObjectNode) welcome.get("intent")).put("name", "웰컴 블록");
        assertThat(service.resolve(welcome)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void 분야를_고르면_뉴스를_가져와_5건짜리_리스트카드로_답한다() throws IOException {
        server.expect(requestTo(POLITICS_RSS)).andRespond(withSuccess(rss(7), MediaType.APPLICATION_XML));

        Map<String, Object> reply = service.reply(payload());

        server.verify();
        JsonNode r = json.valueToTree(reply);
        assertThat(r.get("version").asString()).isEqualTo("2.0");
        JsonNode card = r.at("/template/outputs/0/listCard");
        assertThat(card.at("/header/title").asString()).isEqualTo("📰 정치 뉴스");
        assertThat(card.get("items")).hasSize(5);
        assertThat(card.at("/items/0/title").asString()).isEqualTo("정치 기사 1");
        assertThat(card.at("/items/0/description").asString()).isEqualTo("언론사 · 08:00");
        assertThat(card.at("/items/0/link/web").asString()).isEqualTo("https://news.google.com/rss/articles/1");
        // "더보기"는 같은 내용의 구글 뉴스 웹 페이지로
        assertThat(card.at("/buttons/0/webLinkUrl").asString())
                .isEqualTo("https://news.google.com/search?q=%EC%A0%95%EC%B9%98+when%3A1d&hl=ko&gl=KR&ceid=KR:ko");
        assertThat(r.at("/template/quickReplies")).hasSize(5);
        assertThat(r.at("/template/quickReplies/1/messageText").asString()).isEqualTo("정치");
        assertThat(r.at("/template/quickReplies/1/action").asString()).isEqualTo("message");
    }

    @Test
    void 분야를_모르면_분야_선택_버튼으로_답한다() throws IOException {
        ObjectNode welcome = payload();
        ((ObjectNode) welcome.get("userRequest")).put("utterance", "");
        ((ObjectNode) welcome.at("/userRequest/block")).put("name", "웰컴 블록");
        ((ObjectNode) welcome.get("intent")).put("name", "웰컴 블록");

        JsonNode r = json.valueToTree(service.reply(welcome));

        assertThat(r.at("/template/outputs/0/simpleText/text").asString()).startsWith("어떤 분야 뉴스를 볼까요?");
        assertThat(r.at("/template/quickReplies")).extracting(q -> q.get("label").asString())
                .containsExactly("주요 뉴스", "정치", "경제", "국제", "IT·과학");
    }

    @Test
    void 같은_분야는_5분_동안_다시_가져오지_않고_이후_실패하면_이전_결과로_답한다() throws IOException {
        server.expect(ExpectedCount.once(), requestTo(POLITICS_RSS)).andRespond(withSuccess(rss(3), MediaType.APPLICATION_XML));
        service.reply(payload());
        clock.advance(Duration.ofMinutes(4));
        service.reply(payload()); // 캐시 사용 → 요청 없음
        server.verify();

        server.reset();
        server.expect(requestTo(POLITICS_RSS)).andRespond(withServerError());
        clock.advance(Duration.ofMinutes(2)); // 5분 지남 → 새로 가져오다 실패

        JsonNode r = json.valueToTree(service.reply(payload()));

        server.verify();
        assertThat(r.at("/template/outputs/0/listCard/items")).hasSize(3);
    }

    @Test
    void 처음부터_가져오지_못하면_안내_문구로_답한다() throws IOException {
        server.expect(requestTo(POLITICS_RSS)).andRespond(withServerError());

        JsonNode r = json.valueToTree(service.reply(payload()));

        assertThat(r.at("/template/outputs/0/simpleText/text").asString()).isEqualTo(NewsSkillService.FAILED_TEXT);
        assertThat(r.at("/template/quickReplies")).hasSize(5);
    }

    ObjectNode payload() throws IOException {
        try (var in = getClass().getResourceAsStream("/fixtures/skill-request.json")) {
            return (ObjectNode) json.readTree(in);
        }
    }

    static byte[] rss(int n) {
        String items = String.join("", IntStream.rangeClosed(1, n).mapToObj(i ->
                "<item><title>정치 기사 " + i + " - 언론사</title><link>https://news.google.com/rss/articles/" + i
                        + "</link><pubDate>Fri, 23 Oct 2026 23:00:00 GMT</pubDate></item>").toList());
        return ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><rss version=\"2.0\"><channel><title>정치 - Google 뉴스</title>"
                + "<link>https://news.google.com/</link>" + items + "</channel></rss>").getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    static NewsBotProperties properties() {
        return new NewsBotProperties(
                new NewsBotProperties.Schedule("0 0 9 * * *", ZoneId.of("Asia/Seoul")),
                new NewsBotProperties.News(5, Duration.ofHours(24), "https://news.google.com/home", List.of(
                        new NewsBotProperties.Section("주요 뉴스", "https://news.google.com/rss?hl=ko&gl=KR&ceid=KR:ko", null),
                        new NewsBotProperties.Section("정치", null, "정치"),
                        new NewsBotProperties.Section("경제", BUSINESS_RSS, null),
                        new NewsBotProperties.Section("국제", "https://news.google.com/rss/headlines/section/topic/WORLD?hl=ko", null),
                        new NewsBotProperties.Section("IT·과학", "https://news.google.com/rss/headlines/section/topic/TECHNOLOGY?hl=ko", null))),
                new NewsBotProperties.Http(Duration.ofSeconds(5), Duration.ofSeconds(5), "test"),
                null);
    }

    static ChatbotProperties chatbot() {
        return new ChatbotProperties(true, 5, Duration.ofMinutes(5), Duration.ofMillis(1500), Duration.ofMillis(2500), null);
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
