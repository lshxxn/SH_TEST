package com.example.newsbot;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.example.newsbot.delivery.NewsSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "newsbot.admin-token=test-admin",
        "newsbot.kakao.enabled=true",
        "newsbot.kakao.rest-api-key=rest-key",
        "newsbot.kakao.token-file=target/test-data/kakao-token.json",
        "newsbot.solapi.enabled=true",
        "newsbot.solapi.recipients=01011112222,01033334444",
})
@AutoConfigureMockMvc
class NewsBotApplicationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    List<NewsSender> senders;

    @Test
    void 설정대로_발송_채널이_켜진다() {
        assertThat(senders).extracting(NewsSender::name)
                .containsExactlyInAnyOrder("kakao-memo", "solapi-brand-message");
    }

    @Test
    void 관리자_토큰_없이는_수동발송을_막는다() throws Exception {
        mvc.perform(post("/api/news/send-now")).andExpect(status().isForbidden());
        mvc.perform(post("/api/news/send-now").header("X-Admin-Token", "wrong")).andExpect(status().isForbidden());
        mvc.perform(get("/kakao/login")).andExpect(status().isForbidden());
    }

    @Test
    void 카카오_로그인은_state를_붙여_인가페이지로_보낸다() throws Exception {
        mvc.perform(get("/kakao/login").param("token", "test-admin"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.startsWith("https://kauth.kakao.com/oauth/authorize?response_type=code"),
                        org.hamcrest.Matchers.containsString("client_id=rest-key"),
                        org.hamcrest.Matchers.containsString("scope=talk_message"),
                        org.hamcrest.Matchers.matchesPattern(".*state=[0-9a-f]{32}.*"))));
    }

    @Test
    void 모르는_state로_돌아온_콜백은_거부한다() throws Exception {
        mvc.perform(get("/kakao/callback").param("code", "c").param("state", "forged"))
                .andExpect(status().isBadRequest());
    }
}
