package com.example.newsbot.site;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import com.example.newsbot.config.NewsBotProperties;
import com.example.newsbot.news.NewsArticle;
import com.example.newsbot.news.NewsDigest;
import com.example.newsbot.news.NewsSection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class NewsPageRendererTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-10-23T23:41:00Z"); // 한국 08:41

    @TempDir
    Path dir;

    private final NewsDigest digest = new NewsDigest(LocalDate.of(2026, 10, 24), List.of(
            new NewsSection("정치", List.of(
                    new NewsArticle("국회 예산안 심사 돌입", "https://news.google.com/a/1", "KBS",
                            Instant.parse("2026-10-23T22:10:00Z")),
                    new NewsArticle("<script>alert(1)</script> {{COPY_TEXT}}", "javascript:alert(1)", null, null))),
            new NewsSection("경제", List.of(
                    new NewsArticle("코스피 2700 회복", "https://news.google.com/a/2", "한국경제", null)))));

    @Test
    void 섹션과_기사를_그리고_외부_입력은_안전하게_처리한다() {
        String html = renderer("https://lshxxn.github.io/SH_TEST/", null).render(digest, NOW);

        assertThat(html).contains("<title>10월 24일(토) 오늘의 뉴스 클리핑</title>");
        assertThat(html).contains("오전 8:41 업데이트 · 기사 3건");
        assertThat(html).contains("<a href=\"#s1\">정치</a><a href=\"#s2\">경제</a>");
        assertThat(html).contains("href=\"https://news.google.com/a/1\"");
        assertThat(html).contains("KBS · 07:10");
        // 스크립트·위험한 링크·치환자 주입은 그대로 출력되지 않는다
        assertThat(html).doesNotContain("<script>alert(1)</script>");
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt; {{COPY_TEXT}}");
        assertThat(html).doesNotContain("href=\"javascript:");
        // 채널 주소가 없으면 채널 추가 버튼도 없다
        assertThat(html).doesNotContain("채널 추가하고 매일 받기");
        // 전체 복사 내용에 페이지 주소가 붙는다
        assertThat(html).contains("전체 보기: https://lshxxn.github.io/SH_TEST/</textarea>");
    }

    @Test
    void 채널_주소가_있으면_채널_추가_버튼을_보여준다() {
        String html = renderer(null, "https://pf.kakao.com/_abcd").render(digest, NOW);
        assertThat(html).contains("<a class=\"btn primary\" href=\"https://pf.kakao.com/_abcd\">채널 추가하고 매일 받기</a>");
    }

    @Test
    void 페이지와_챗봇용_JSON_파일을_만든다() throws IOException {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        SiteProperties site = new SiteProperties(true, dir.resolve("_site"), "오늘의 뉴스 클리핑",
                "https://lshxxn.github.io/SH_TEST/", null);
        SitePublisher publisher = new SitePublisher(null, site, properties(), jsonMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));

        publisher.publish(digest, NOW);

        assertThat(dir.resolve("_site/index.html")).exists();
        assertThat(dir.resolve("_site/.nojekyll")).exists();
        JsonNode json = jsonMapper.readTree(Files.readString(dir.resolve("_site/news.json")));
        assertThat(json.get("date").asString()).isEqualTo("2026-10-24");
        assertThat(json.get("dateLabel").asString()).isEqualTo("10월 24일(토)");
        assertThat(json.get("pageUrl").asString()).isEqualTo("https://lshxxn.github.io/SH_TEST/");
        assertThat(json.get("sections")).hasSize(2);
        JsonNode first = json.get("sections").get(0).get("articles").get(0);
        assertThat(first.get("title").asString()).isEqualTo("국회 예산안 심사 돌입");
        assertThat(first.get("meta").asString()).isEqualTo("KBS · 07:10");
    }

    private static NewsPageRenderer renderer(String pageUrl, String channelUrl) {
        return new NewsPageRenderer(new SiteProperties(true, Path.of("_site"), "오늘의 뉴스 클리핑", pageUrl, channelUrl), SEOUL);
    }

    private static NewsBotProperties properties() {
        return new NewsBotProperties(
                new NewsBotProperties.Schedule("0 0 9 * * *", SEOUL),
                new NewsBotProperties.News(5, Duration.ofHours(24), "https://news.google.com/home", List.of()),
                new NewsBotProperties.Http(Duration.ofSeconds(5), Duration.ofSeconds(5), "test"),
                null);
    }
}
