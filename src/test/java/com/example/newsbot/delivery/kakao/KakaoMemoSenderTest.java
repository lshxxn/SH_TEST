package com.example.newsbot.delivery.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import com.example.newsbot.news.NewsDigest;
import com.example.newsbot.news.NewsSection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class KakaoMemoSenderTest {

    @TempDir
    Path dir;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"), ZoneOffset.UTC);
    private MockRestServiceServer server;
    private KakaoTokenStore store;
    private KakaoMemoSender sender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        KakaoProperties props = KakaoTestSupport.properties(dir.resolve("token.json"), null);
        store = new KakaoTokenStore(props.tokenFile(), jsonMapper);
        KakaoTokenService tokens = new KakaoTokenService(new KakaoOAuthClient(props, builder), store, clock);
        sender = new KakaoMemoSender(props, tokens,
                new KakaoTemplateFactory("https://news.google.com/home", ZoneId.of("Asia/Seoul")), builder, jsonMapper);
    }

    @Test
    void 섹션을_리스트_템플릿으로_나눠_나에게_보낸다() {
        store.save(new KakaoToken("at", Instant.parse("2026-10-24T05:00:00Z"), "rt", null, "talk_message"));
        List<JsonNode> sent = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            server.expect(requestTo("https://kapi.kakao.com/v2/api/talk/memo/default/send"))
                    .andExpect(method(HttpMethod.POST))
                    .andExpect(header("Authorization", "Bearer at"))
                    .andExpect(req -> assertThat(req.getHeaders().getContentType().toString())
                            .startsWith("application/x-www-form-urlencoded"))
                    .andExpect(req -> sent.add(templateObject(req)))
                    .andRespond(withSuccess("{\"result_code\":0}", MediaType.APPLICATION_JSON));
        }

        sender.send(digest(5));

        server.verify();
        assertThat(sent).hasSize(2);
        assertThat(sent.get(0).get("object_type").asString()).isEqualTo("list");
        assertThat(sent.get(0).get("contents")).hasSize(3);
        assertThat(sent.get(1).get("contents")).hasSize(2);
        assertThat(sent.get(0).get("contents").get(0).get("link").get("web_url").asString())
                .isEqualTo("https://news.google.com/a/1");
    }

    @Test
    void 인증오류_401을_받으면_토큰을_갱신하고_한번_재시도한다() {
        store.save(new KakaoToken("revoked", Instant.parse("2026-10-24T05:00:00Z"), "rt", null, "talk_message"));
        server.expect(requestTo("https://kapi.kakao.com/v2/api/talk/memo/default/send"))
                .andExpect(header("Authorization", "Bearer revoked"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"msg\":\"this access token does not exist\",\"code\":-401}"));
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andRespond(withSuccess(KakaoTestSupport.tokenJson("fresh", null), MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://kapi.kakao.com/v2/api/talk/memo/default/send"))
                .andExpect(header("Authorization", "Bearer fresh"))
                .andRespond(withSuccess("{\"result_code\":0}", MediaType.APPLICATION_JSON));

        sender.send(digest(2));

        server.verify();
        assertThat(store.load().orElseThrow().accessToken()).isEqualTo("fresh");
    }

    private NewsDigest digest(int n) {
        return new NewsDigest(LocalDate.of(2026, 10, 24),
                List.of(new NewsSection("주요 뉴스", KakaoTemplateFactoryTest.articles(n))));
    }

    private JsonNode templateObject(org.springframework.http.client.ClientHttpRequest request) {
        String body = ((MockClientHttpRequest) request).getBodyAsString(StandardCharsets.UTF_8);
        assertThat(body).startsWith("template_object=");
        String json = URLDecoder.decode(body.substring("template_object=".length()), StandardCharsets.UTF_8);
        return jsonMapper.readTree(json);
    }
}
