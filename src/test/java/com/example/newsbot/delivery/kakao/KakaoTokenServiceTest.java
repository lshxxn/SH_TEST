package com.example.newsbot.delivery.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class KakaoTokenServiceTest {

    @TempDir
    Path dir;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"), ZoneOffset.UTC);
    private MockRestServiceServer server;
    private KakaoTokenStore store;
    private KakaoTokenService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        KakaoProperties props = KakaoTestSupport.properties(dir.resolve("data/token.json"), "secret");
        store = new KakaoTokenStore(props.tokenFile(), JsonMapper.builder().build());
        service = new KakaoTokenService(new KakaoOAuthClient(props, builder), store, clock);
    }

    @Test
    void 인가코드로_토큰을_발급받아_파일에_저장한다() throws Exception {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", "rest-key");
        form.add("client_secret", "secret");
        form.add("redirect_uri", "http://localhost:8080/kakao/callback");
        form.add("code", "auth-code");
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formData(form))
                .andRespond(withSuccess(KakaoTestSupport.tokenJson("at-1", "rt-1"), MediaType.APPLICATION_JSON));

        KakaoToken token = service.issue("auth-code");

        server.verify();
        assertThat(token.accessToken()).isEqualTo("at-1");
        assertThat(token.accessTokenExpiresAt()).isEqualTo(Instant.parse("2026-10-24T05:59:59Z"));
        assertThat(store.load()).contains(token);
        assertThat(Files.getPosixFilePermissions(store.file())).hasSize(2); // rw-------
    }

    @Test
    void 만료된_액세스토큰은_갱신하고_응답에_리프레시토큰이_없으면_기존값을_유지한다() {
        store.save(new KakaoToken("old-at", Instant.parse("2026-10-23T12:00:00Z"), "rt-1",
                Instant.parse("2026-12-20T00:00:00Z"), "talk_message"));
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", "rest-key");
        form.add("client_secret", "secret");
        form.add("refresh_token", "rt-1");
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andExpect(content().formData(form))
                .andRespond(withSuccess(KakaoTestSupport.tokenJson("new-at", null), MediaType.APPLICATION_JSON));

        assertThat(service.validAccessToken()).isEqualTo("new-at");

        KakaoToken saved = store.load().orElseThrow();
        assertThat(saved.refreshToken()).isEqualTo("rt-1");
        assertThat(saved.refreshTokenExpiresAt()).isEqualTo(Instant.parse("2026-12-20T00:00:00Z"));
    }

    @Test
    void 리프레시토큰이_새로_오면_교체한다() {
        store.save(new KakaoToken("old-at", Instant.parse("2026-10-23T12:00:00Z"), "rt-1",
                Instant.parse("2026-11-10T00:00:00Z"), "talk_message"));
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andRespond(withSuccess(KakaoTestSupport.tokenJson("new-at", "rt-2"), MediaType.APPLICATION_JSON));

        service.validAccessToken();

        KakaoToken saved = store.load().orElseThrow();
        assertThat(saved.refreshToken()).isEqualTo("rt-2");
        assertThat(saved.refreshTokenExpiresAt()).isAfter(Instant.parse("2026-12-01T00:00:00Z"));
    }

    @Test
    void 유효한_액세스토큰은_갱신하지_않는다() {
        store.save(new KakaoToken("at", Instant.parse("2026-10-24T03:00:00Z"), "rt", null, null));
        assertThat(service.validAccessToken()).isEqualTo("at");
        server.verify(); // 요청 없음
    }

    @Test
    void 토큰이_없으면_로그인_안내와_함께_실패한다() {
        assertThatThrownBy(service::validAccessToken)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("/kakao/login");
    }

    @Test
    void 갱신이_거부되면_응답본문과_재로그인_안내를_담는다() {
        store.save(new KakaoToken("old", Instant.parse("2026-10-23T00:00:00Z"), "rt", null, null));
        server.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\",\"error_code\":\"KOE322\"}"));

        assertThatThrownBy(service::validAccessToken)
                .isInstanceOf(KakaoApiException.class)
                .hasMessageContaining("invalid_grant")
                .hasMessageContaining("다시 로그인");
    }
}
