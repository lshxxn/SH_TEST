package com.example.newsbot.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class RssParserTest {

    static byte[] fixture(String name) throws IOException {
        try (var in = RssParserTest.class.getResourceAsStream("/fixtures/" + name)) {
            return in.readAllBytes();
        }
    }

    @Test
    void 구글뉴스_제목에서_언론사를_분리한다() throws IOException {
        List<NewsArticle> articles = RssParser.parse(fixture("google-news.xml"));

        assertThat(articles).hasSize(4); // 제목이 빈 항목은 제외
        NewsArticle first = articles.getFirst();
        assertThat(first.title()).isEqualTo("반도체 수출 석 달 연속 증가");
        assertThat(first.source()).isEqualTo("연합뉴스");
        assertThat(first.link()).isEqualTo("https://news.google.com/rss/articles/AAA?oc=5");
        assertThat(first.publishedAt()).isEqualTo(Instant.parse("2026-10-23T23:12:00Z"));

        // 제목 안에 " - " 가 있어도 마지막 것만 언론사로 본다
        assertThat(articles.get(1).title()).isEqualTo("\"AI 기본법\" 시행 앞두고 - 업계 준비 분주");
        assertThat(articles.get(1).source()).isEqualTo("한국경제");
    }

    @Test
    void 일반_RSS는_피드_제목을_출처로_쓰고_제목을_자르지_않는다() throws IOException {
        List<NewsArticle> articles = RssParser.parse(fixture("publisher.xml"));

        assertThat(articles).hasSize(2);
        assertThat(articles.get(1).title()).isEqualTo("새 스마트폰 공개 - 접는 화면");
        assertThat(articles.get(1).source()).isEqualTo("테크신문 전체기사");
        assertThat(articles.get(1).publishedAt()).isNull();
    }

    @Test
    void DOCTYPE이_있는_XML은_거부한다() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE rss [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <rss version="2.0"><channel><title>&xxe;</title><link>https://a</link></channel></rss>
                """;
        assertThatThrownBy(() -> RssParser.parse(xxe.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
