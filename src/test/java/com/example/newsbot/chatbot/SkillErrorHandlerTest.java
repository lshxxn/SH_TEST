package com.example.newsbot.chatbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SkillErrorHandlerTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-25T04:30:00Z"), ZoneOffset.UTC);

    @Test
    void 서버_안에서_예외가_나도_카카오에는_정상_말풍선으로_답하고_상태_페이지에_남긴다() throws Exception {
        NewsSkillService broken = new NewsSkillService(NewsSkillServiceTest.properties(), NewsSkillServiceTest.chatbot(),
                clock, RestClient.builder().build()) {
            @Override
            public Map<String, Object> reply(JsonNode payload) {
                throw new IllegalStateException("예상 못 한 문제");
            }
        };
        RecentErrors errors = new RecentErrors(clock, ZoneId.of("Asia/Seoul"));
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new KakaoSkillController(broken, new SkillTokenGuard(null), JsonMapper.builder().build(), errors))
                .setControllerAdvice(new SkillErrorHandler(broken, errors))
                .build();

        mvc.perform(post("/kakao/skill/news").contentType(MediaType.APPLICATION_JSON).content("{\"userRequest\":{\"utterance\":\"정치\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("2.0"))
                .andExpect(jsonPath("$.template.outputs[0].simpleText.text").value(SkillErrorHandler.FALLBACK_TEXT))
                .andExpect(jsonPath("$.template.quickReplies.length()").value(5));

        assertThat(errors.list()).hasSize(1);
        assertThat(errors.list().getFirst()).startsWith("10-25 13:30:00 POST /kakao/skill/news → IllegalStateException: 예상 못 한 문제");
        mvc.perform(get("/kakao/skill/news"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("최근 오류 1건")));
    }

    @Test
    void 잘못된_JSON_요청도_정상_말풍선으로_답한다() throws Exception {
        NewsSkillService service = new NewsSkillService(NewsSkillServiceTest.properties(), NewsSkillServiceTest.chatbot(),
                clock, RestClient.builder().build());
        RecentErrors errors = new RecentErrors(clock, ZoneId.of("Asia/Seoul"));
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new KakaoSkillController(service, new SkillTokenGuard(null), JsonMapper.builder().build(), errors))
                .setControllerAdvice(new SkillErrorHandler(service, errors))
                .build();

        mvc.perform(post("/kakao/skill/news").contentType(MediaType.APPLICATION_JSON).content("{이건 JSON 이 아님"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.template.outputs[0].simpleText.text").value(SkillErrorHandler.FALLBACK_TEXT));
    }
}
