package com.example.newsbot.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import com.example.newsbot.config.NewsBotProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NewsCollectorTest {

    // 한국 시간 2026-10-24 09:00
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-24T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void 섹션별로_수집하고_오래된_기사와_중복을_거르며_실패한_피드는_건너뛴다() throws IOException {
        NewsBotProperties props = properties(List.of(
                new NewsBotProperties.Section("주요 뉴스", "https://news.google.com/rss?hl=ko", null),
                new NewsBotProperties.Section("고장난 피드", "https://broken.example.com/rss", null),
                new NewsBotProperties.Section("테크", "https://tech.example.com/rss", null)), 2);

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(ExpectedCount.once(), requestTo("https://news.google.com/rss?hl=ko"))
                .andRespond(withSuccess(RssParserTest.fixture("google-news.xml"), MediaType.APPLICATION_XML));
        server.expect(ExpectedCount.once(), requestTo("https://broken.example.com/rss"))
                .andRespond(withServerError());
        server.expect(ExpectedCount.once(), requestTo("https://tech.example.com/rss"))
                .andRespond(withSuccess(RssParserTest.fixture("publisher.xml"), MediaType.APPLICATION_XML));

        NewsDigest digest = new NewsCollector(builder, props, clock).collect();

        server.verify();
        assertThat(digest.date()).isEqualTo(LocalDate.of(2026, 10, 24));
        assertThat(digest.sections()).extracting(NewsSection::name).containsExactly("주요 뉴스", "테크");
        // 섹션당 최대 2건
        assertThat(digest.sections().get(0).articles()).extracting(NewsArticle::title)
                .containsExactly("반도체 수출 석 달 연속 증가", "\"AI 기본법\" 시행 앞두고 - 업계 준비 분주");
        // 같은 제목의 기사는 앞 섹션에서 이미 나왔으므로 제외
        assertThat(digest.sections().get(1).articles()).extracting(NewsArticle::title)
                .containsExactly("새 스마트폰 공개 - 접는 화면");
    }

    @Test
    void lookback보다_오래된_기사는_제외한다() throws IOException {
        NewsBotProperties props = properties(List.of(
                new NewsBotProperties.Section("주요 뉴스", "https://news.google.com/rss?hl=ko", null)), 10);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://news.google.com/rss?hl=ko"))
                .andRespond(withSuccess(RssParserTest.fixture("google-news.xml"), MediaType.APPLICATION_XML));

        NewsDigest digest = new NewsCollector(builder, props, clock).collect();

        assertThat(digest.sections().getFirst().articles()).extracting(NewsArticle::title)
                .doesNotContain("오래된 기사")
                .hasSize(3);
    }

    @Test
    void 키워드_섹션은_구글뉴스_검색_RSS로_바뀐다() {
        var section = new NewsBotProperties.Section(null, null, "인공지능 반도체");
        assertThat(section.feedUri().toString()).isEqualTo(
                "https://news.google.com/rss/search?q=%EC%9D%B8%EA%B3%B5%EC%A7%80%EB%8A%A5+%EB%B0%98%EB%8F%84%EC%B2%B4+when%3A1d&hl=ko&gl=KR&ceid=KR:ko");
        assertThat(section.displayName()).isEqualTo("인공지능 반도체");
    }

    static NewsBotProperties properties(List<NewsBotProperties.Section> sections, int maxItems) {
        return new NewsBotProperties(
                new NewsBotProperties.Schedule("0 0 9 * * *", ZoneId.of("Asia/Seoul")),
                new NewsBotProperties.News(maxItems, Duration.ofHours(24), "https://news.google.com/home", sections),
                new NewsBotProperties.Http(Duration.ofSeconds(5), Duration.ofSeconds(5), "test"),
                "admin");
    }
}
