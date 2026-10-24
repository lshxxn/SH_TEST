package com.example.newsbot.site;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import com.example.newsbot.delivery.MessageText;
import com.example.newsbot.news.NewsDigest;

/**
 * 챗봇이 읽어 가는 news.json 형식.
 */
public record NewsJson(
        String date,
        String dateLabel,
        Instant updatedAt,
        String pageUrl,
        List<Section> sections) {

    public record Section(String name, List<Article> articles) {
    }

    public record Article(String title, String link, String source, Instant publishedAt, String meta) {
    }

    public static NewsJson from(NewsDigest digest, Instant updatedAt, String pageUrl, ZoneId zone) {
        List<Section> sections = digest.sections().stream()
                .map(s -> new Section(s.name(), s.articles().stream()
                        .map(a -> new Article(a.title(), a.link(), a.source(), a.publishedAt(),
                                MessageText.sourceLine(a.source(), a.publishedAt(), zone)))
                        .toList()))
                .toList();
        return new NewsJson(digest.date().toString(), MessageText.dateLabel(digest.date()), updatedAt, pageUrl, sections);
    }
}
