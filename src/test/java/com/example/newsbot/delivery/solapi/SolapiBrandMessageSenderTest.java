package com.example.newsbot.delivery.solapi;

import com.example.newsbot.delivery.DigestTextFormatter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

import com.example.newsbot.news.NewsArticle;
import com.example.newsbot.news.NewsDigest;
import com.example.newsbot.news.NewsSection;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class SolapiBrandMessageSenderTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void 채널_친구에게_BMS_FREE_텍스트로_보낸다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.solapi.com/messages/v4/send-many/detail"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(req -> org.assertj.core.api.Assertions.assertThat(req.getHeaders().getContentLength()).isPositive())
                .andExpect(header("Authorization", Matchers.startsWith(
                        "HMAC-SHA256 apiKey=key, date=2026-10-24T09:00:00+09:00, salt=")))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].to").value("01011112222"))
                .andExpect(jsonPath("$.messages[1].to").value("01033334444"))
                .andExpect(jsonPath("$.messages[0].type").value("BMS_FREE"))
                .andExpect(jsonPath("$.messages[0].from").doesNotExist())
                .andExpect(jsonPath("$.messages[0].text").value(Matchers.startsWith("📰 10월 24일(토) 아침 뉴스\n\n[주요 뉴스]\n1. 기사 1 (연합뉴스)\nhttps://n.example/1")))
                .andExpect(jsonPath("$.messages[0].kakaoOptions.pfId").value("KA01PF-test"))
                .andExpect(jsonPath("$.messages[0].kakaoOptions.disableSms").value(true))
                .andExpect(jsonPath("$.messages[0].kakaoOptions.bms.targeting").value("I"))
                .andExpect(jsonPath("$.messages[0].kakaoOptions.bms.chatBubbleType").value("TEXT"))
                .andExpect(jsonPath("$.messages[0].kakaoOptions.bms.buttons[0].name").value("뉴스 더보기"))
                .andExpect(jsonPath("$.messages[0].kakaoOptions.bms.buttons[0].linkType").value("WL"))
                .andExpect(jsonPath("$.messages[0].kakaoOptions.bms.buttons[0].linkMobile").value("https://news.google.com/home"))
                .andRespond(withSuccess("""
                        {"failedMessageList":[],"groupInfo":{"groupId":"G1","count":{"total":2,"registeredFailed":0,"registeredSuccess":2}}}
                        """, MediaType.APPLICATION_JSON));

        sender(builder, List.of("010-1111-2222", "01033334444", "010 3333 4444")).send(digest(3));

        server.verify();
    }

    @Test
    void 모두_접수_실패하면_예외를_던진다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.solapi.com/messages/v4/send-many/detail"))
                .andRespond(withSuccess("""
                        {"failedMessageList":[{"to":"01011112222","statusCode":"1062","statusMessage":"발신프로필 키가 유효하지 않습니다."}],
                         "groupInfo":{"count":{"total":1,"registeredFailed":1,"registeredSuccess":0}}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> sender(builder, List.of("01011112222")).send(digest(1)))
                .hasMessageContaining("1062");
    }

    @Test
    void 필수_설정이_없으면_요청하지_않고_실패한다() {
        SolapiProperties props = new SolapiProperties(true, "", "", "", null, List.of("01011112222"),
                "I", true, true, 1300, "https://api.solapi.com");
        var sender = new SolapiBrandMessageSender(props, null, ZoneId.of("Asia/Seoul"), clock, RestClient.builder(), JsonMapper.builder().build());

        assertThatThrownBy(() -> sender.send(digest(1)))
                .hasMessageContaining("SOLAPI_API_KEY").hasMessageContaining("SOLAPI_PF_ID");
    }

    @Test
    void 본문은_최대_길이를_넘지_않는다() {
        List<NewsArticle> many = IntStream.rangeClosed(1, 30)
                .mapToObj(i -> new NewsArticle("아주 긴 제목 ".repeat(5) + i,
                        "https://news.google.com/rss/articles/" + "x".repeat(150), "언론사", null))
                .toList();
        NewsDigest digest = new NewsDigest(LocalDate.of(2026, 10, 24), List.of(new NewsSection("주요 뉴스", many)));

        DigestTextFormatter.Result result = DigestTextFormatter.format(digest, true, 1300);

        assertThat(result.text().codePointCount(0, result.text().length())).isLessThanOrEqualTo(1300);
        assertThat(result.includedArticles()).isBetween(1, 29);
    }

    @Test
    void 전화번호는_로그에서_가린다() {
        assertThat(SolapiBrandMessageSender.mask("01012345678")).isEqualTo("010****5678");
    }

    private SolapiBrandMessageSender sender(RestClient.Builder builder, List<String> recipients) {
        SolapiProperties props = new SolapiProperties(true, "key", "secret", "KA01PF-test", null, recipients,
                "I", true, true, 1300, "https://api.solapi.com");
        return new SolapiBrandMessageSender(props, "https://news.google.com/home", ZoneId.of("Asia/Seoul"), clock, builder, JsonMapper.builder().build());
    }

    private static NewsDigest digest(int n) {
        List<NewsArticle> articles = IntStream.rangeClosed(1, n)
                .mapToObj(i -> new NewsArticle("기사 " + i, "https://n.example/" + i, "연합뉴스", null))
                .toList();
        return new NewsDigest(LocalDate.of(2026, 10, 24), List.of(new NewsSection("주요 뉴스", articles)));
    }
}
