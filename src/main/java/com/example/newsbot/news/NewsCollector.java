package com.example.newsbot.news;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.newsbot.config.NewsBotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 설정된 섹션별 RSS 를 내려받아 하루치 {@link NewsDigest} 를 만든다.
 * 피드 하나가 실패해도 나머지 섹션은 계속 진행한다.
 */
@Component
public class NewsCollector {

    private static final Logger log = LoggerFactory.getLogger(NewsCollector.class);

    private final RestClient restClient;
    private final NewsBotProperties properties;
    private final Clock clock;

    public NewsCollector(RestClient.Builder newsBotRestClientBuilder, NewsBotProperties properties, Clock clock) {
        this.restClient = newsBotRestClientBuilder.clone().build();
        this.properties = properties;
        this.clock = clock;
    }

    public NewsDigest collect() {
        NewsBotProperties.News news = properties.news();
        Instant cutoff = clock.instant().minus(news.lookback());
        Set<String> seen = new HashSet<>(); // 섹션 간 중복 기사 제거

        List<NewsSection> sections = new ArrayList<>();
        for (NewsBotProperties.Section section : news.sections()) {
            List<NewsArticle> fetched;
            try {
                fetched = fetch(section.feedUri());
            } catch (RuntimeException e) {
                log.warn("섹션 '{}' 피드를 가져오지 못했습니다: {}", section.displayName(), e.getMessage());
                continue;
            }
            List<NewsArticle> picked = pick(fetched, cutoff, news.maxItemsPerSection(), seen);
            log.info("섹션 '{}': {}건 중 {}건 선택", section.displayName(), fetched.size(), picked.size());
            if (!picked.isEmpty()) {
                sections.add(new NewsSection(section.displayName(), picked));
            }
        }
        return new NewsDigest(LocalDate.now(clock.withZone(properties.schedule().zone())), sections);
    }

    /**
     * 기한(cutoff)보다 오래된 기사와 이미 나온 기사(seen)를 빼고 앞에서부터 max 건을 고른다.
     */
    public static List<NewsArticle> pick(List<NewsArticle> fetched, Instant cutoff, int max, Set<String> seen) {
        List<NewsArticle> picked = new ArrayList<>();
        for (NewsArticle article : fetched) {
            if (picked.size() >= max) {
                break;
            }
            if (article.publishedAt() != null && article.publishedAt().isBefore(cutoff)) {
                continue;
            }
            if (!seen.add(dedupKey(article))) {
                continue;
            }
            picked.add(article);
        }
        return picked;
    }

    private List<NewsArticle> fetch(URI uri) {
        byte[] body = restClient.get().uri(uri).retrieve().body(byte[].class);
        if (body == null || body.length == 0) {
            throw new IllegalStateException("빈 응답: " + uri);
        }
        return RssParser.parse(body);
    }

    private static String dedupKey(NewsArticle article) {
        return article.title().replaceAll("[\\s\\p{Punct}]", "").toLowerCase();
    }
}
