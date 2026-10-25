package com.example.newsbot.chatbot;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class KakaoSkillControllerTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void 카카오_스킬_요청에_리스트카드로_답한다() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(NewsSkillServiceTest.POLITICS_RSS))
                .andRespond(withSuccess(NewsSkillServiceTest.rss(6), MediaType.APPLICATION_XML));
        MockMvc mvc = mvc(builder, null);

        mvc.perform(post("/kakao/skill/news").contentType(MediaType.APPLICATION_JSON).content(request()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.version").value("2.0"))
                .andExpect(jsonPath("$.template.outputs[0].listCard.header.title").value("📰 정치 뉴스"))
                .andExpect(jsonPath("$.template.outputs[0].listCard.items.length()").value(5))
                .andExpect(jsonPath("$.template.quickReplies.length()").value(5));
        server.verify();
    }

    @Test
    void 토큰을_설정하면_토큰이_맞을_때만_답한다() throws Exception {
        MockMvc mvc = mvc(RestClient.builder(), "secret-1");
        String welcome = request().replace("\"utterance\": \"정치\"", "\"utterance\": \"\"")
                .replace("정치 뉴스", "웰컴 블록");

        mvc.perform(post("/kakao/skill/news").contentType(MediaType.APPLICATION_JSON).content(welcome))
                .andExpect(status().isForbidden());
        mvc.perform(post("/kakao/skill/news").param("token", "wrong").contentType(MediaType.APPLICATION_JSON).content(welcome))
                .andExpect(status().isForbidden());
        mvc.perform(post("/kakao/skill/news").param("token", "secret-1").contentType(MediaType.APPLICATION_JSON).content(welcome))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.template.outputs[0].simpleText.text").exists());
    }

    @Test
    void 브라우저로_열면_동작_중이라고_알려_주고_say를_붙이면_응답을_미리_보여준다() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(NewsSkillServiceTest.POLITICS_RSS))
                .andRespond(withSuccess(NewsSkillServiceTest.rss(2), MediaType.APPLICATION_XML));
        MockMvc mvc = mvc(builder, "secret-1"); // 미리보기는 토큰 없이도 됨

        mvc.perform(get("/kakao/skill/news"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("뉴스 챗봇 스킬 서버가 동작 중입니다.")));
        mvc.perform(get("/kakao/skill/news").param("say", "정치"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.template.outputs[0].listCard.items.length()").value(2));
    }

    private MockMvc mvc(RestClient.Builder builder, String token) {
        ChatbotProperties chatbot = new ChatbotProperties(true, 5, Duration.ofMinutes(5),
                Duration.ofMillis(1500), Duration.ofMillis(2500), token);
        NewsSkillService service = new NewsSkillService(NewsSkillServiceTest.properties(), chatbot, clock, builder.build());
        return MockMvcBuilders.standaloneSetup(new KakaoSkillController(service, new SkillTokenGuard(token), JsonMapper.builder().build(), new RecentErrors(clock, java.time.ZoneId.of("Asia/Seoul")))).build();
    }

    private static String request() throws Exception {
        try (var in = KakaoSkillControllerTest.class.getResourceAsStream("/fixtures/skill-request.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
