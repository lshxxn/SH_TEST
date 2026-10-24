package com.example.newsbot.delivery.kakao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import com.example.newsbot.news.NewsArticle;
import com.example.newsbot.news.NewsDigest;
import com.example.newsbot.news.NewsSection;
import org.junit.jupiter.api.Test;

class KakaoTemplateFactoryTest {

    private final KakaoTemplateFactory factory =
            new KakaoTemplateFactory("https://news.google.com/home", ZoneId.of("Asia/Seoul"));

    @Test
    void 리스트_템플릿에_1개짜리_묶음이_생기지_않게_나눈다() {
        assertThat(sizes(1)).containsExactly(1);
        assertThat(sizes(2)).containsExactly(2);
        assertThat(sizes(3)).containsExactly(3);
        assertThat(sizes(4)).containsExactly(2, 2);
        assertThat(sizes(5)).containsExactly(3, 2);
        assertThat(sizes(7)).containsExactly(3, 2, 2);
        assertThat(sizes(10)).containsExactly(3, 3, 2, 2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 섹션을_리스트_템플릿으로_만든다() {
        NewsDigest digest = new NewsDigest(LocalDate.of(2026, 10, 24), List.of(
                new NewsSection("주요 뉴스", articles(5))));

        List<Map<String, Object>> templates = factory.build(digest);

        assertThat(templates).hasSize(2);
        Map<String, Object> first = templates.getFirst();
        assertThat(first.get("object_type")).isEqualTo("list");
        assertThat(first.get("header_title")).isEqualTo("📰 10월 24일(토) 주요 뉴스 (1/2)");
        assertThat(first.get("header_link")).isEqualTo(Map.of(
                "web_url", "https://news.google.com/home", "mobile_web_url", "https://news.google.com/home"));
        List<Map<String, Object>> contents = (List<Map<String, Object>>) first.get("contents");
        assertThat(contents).hasSize(3);
        assertThat(contents.getFirst().get("title")).isEqualTo("기사 1");
        assertThat(contents.getFirst().get("description")).isEqualTo("연합뉴스 · 08:30");
        assertThat(contents.getFirst().get("link")).isEqualTo(Map.of(
                "web_url", "https://news.google.com/a/1", "mobile_web_url", "https://news.google.com/a/1"));
        assertThat((List<?>) templates.get(1).get("contents")).hasSize(2);
    }

    @Test
    void 기사가_1건뿐인_섹션은_200자_이내_텍스트_템플릿으로_보낸다() {
        String longTitle = "가".repeat(300);
        NewsDigest digest = new NewsDigest(LocalDate.of(2026, 10, 24), List.of(
                new NewsSection("경제", List.of(new NewsArticle(longTitle, "https://news.google.com/a/x", "SBS", null)))));

        Map<String, Object> template = factory.build(digest).getFirst();

        assertThat(template.get("object_type")).isEqualTo("text");
        String text = (String) template.get("text");
        assertThat(text).startsWith("📰 10월 24일(토) 경제\n\n가가");
        assertThat(text.codePointCount(0, text.length())).isEqualTo(200);
        assertThat(text).endsWith("…");
        assertThat(template.get("button_title")).isEqualTo("기사 보기");
    }

    private static List<Integer> sizes(int n) {
        return KakaoTemplateFactory.chunk(IntStream.range(0, n).boxed().toList()).stream().map(List::size).toList();
    }

    static List<NewsArticle> articles(int n) {
        return IntStream.rangeClosed(1, n)
                .mapToObj(i -> new NewsArticle("기사 " + i, "https://news.google.com/a/" + i, "연합뉴스",
                        Instant.parse("2026-10-23T23:30:00Z")))
                .toList();
    }
}
