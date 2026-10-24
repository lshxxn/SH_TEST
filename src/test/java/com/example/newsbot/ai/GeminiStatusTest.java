package com.example.newsbot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class GeminiStatusTest {

    private static final String MODEL_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest";
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path dir;

    @Test
    void 키에_붙은_따옴표와_공백을_지운다() {
        assertThat(GeminiClientTest.properties("  \"AIza-test\"  ", "gemini-flash-lite-latest").apiKey()).isEqualTo("AIza-test");
        assertThat(GeminiClientTest.properties("'AIza-test'", " 'gemini-x' ").modelName()).isEqualTo("gemini-x");
        assertThat(GeminiClientTest.properties("   ", "m").configured()).isFalse();
    }

    @Test
    void 키_파일이_없으면_만들_위치를_알려준다() {
        GeminiStatus.Result r = status("", null).check();
        assertThat(r.state()).isEqualTo(GeminiStatus.State.NO_KEY);
        assertThat(r.message()).contains("이 위치에 secrets.properties 파일을 만드세요: " + dir.resolve("secrets.properties"));
    }

    @Test
    void 예시_파일에_키를_넣은_실수를_알려준다() throws IOException {
        Files.writeString(dir.resolve("secrets.properties.example"), "# 설명\nGEMINI_API_KEY=AIza-secret-value\n");
        GeminiStatus.Result r = status("", null).check();
        assertThat(r.message()).contains("secrets.properties.example 에 키를 넣으셨네요").doesNotContain("AIza-secret-value");
    }

    @Test
    void 파일은_있는데_키가_비었으면_알려준다() throws IOException {
        Files.writeString(dir.resolve("secrets.properties"), "GEMINI_API_KEY=\n");
        assertThat(status("", null).check().message()).contains("GEMINI_API_KEY= 뒤에 키가 비어 있습니다");
    }

    @Test
    void 키가_맞으면_정상_틀리면_고칠_곳을_알려준다() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(MODEL_URL)).andRespond(withSuccess("{\"displayName\":\"Gemini Flash-Lite\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(MODEL_URL + ":generateContent"))
                .andRespond(withSuccess("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"안녕하세요\"}]}}]}", MediaType.APPLICATION_JSON));
        GeminiStatus.Result ok = status("good-key", builder.build()).check();
        assertThat(ok.state()).isEqualTo(GeminiStatus.State.OK);
        assertThat(ok.message()).startsWith("연결 확인 완료: Gemini Flash-Lite (gemini-flash-lite-latest), 시험 응답 ")
                .contains("생각 단계: {thinkingLevel=MINIMAL}")
                .doesNotContain("느립니다");

        server.reset();
        server.expect(requestTo(MODEL_URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .body("{\"error\":{\"details\":[{\"reason\":\"API_KEY_INVALID\"}]}}"));
        GeminiStatus.Result bad = status("bad-key", builder.build()).check();
        assertThat(bad.state()).isEqualTo(GeminiStatus.State.FAILED);
        assertThat(bad.message()).contains("키가 올바르지 않습니다").doesNotContain("bad-key");
    }

    private GeminiStatus status(String key, RestClient rc) {
        GeminiProperties props = GeminiClientTest.properties(key, "gemini-flash-lite-latest");
        RestClient client = rc != null ? rc : RestClient.builder().build();
        return new GeminiStatus(props, new GeminiClient(props, JsonMapper.builder().build(), client, client), dir, clock);
    }
}
