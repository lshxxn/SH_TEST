package com.example.newsbot.news;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

/**
 * RSS 2.0 / Atom 피드를 {@link NewsArticle} 목록으로 바꾼다.
 */
public final class RssParser {

    private static final String GOOGLE_NEWS_HOST = "news.google.com";

    private RssParser() {
    }

    public static List<NewsArticle> parse(byte[] xml) {
        SyndFeed feed;
        try (XmlReader reader = new XmlReader(new ByteArrayInputStream(xml))) {
            SyndFeedInput input = new SyndFeedInput();
            input.setAllowDoctypes(false); // XXE 방지
            feed = input.build(reader);
        } catch (IOException | FeedException e) {
            throw new IllegalArgumentException("RSS 파싱 실패: " + e.getMessage(), e);
        }

        boolean googleNews = isGoogleNews(feed);
        String feedTitle = clean(feed.getTitle());
        List<NewsArticle> articles = new ArrayList<>();
        for (SyndEntry entry : feed.getEntries()) {
            String title = clean(entry.getTitle());
            String link = firstNonBlank(entry.getLink(), entry.getUri());
            if (title.isEmpty() || link == null) {
                continue;
            }
            String source = feedTitle.isEmpty() ? null : feedTitle;
            // 구글 뉴스 제목은 "기사 제목 - 언론사" 형식이다.
            int dash = title.lastIndexOf(" - ");
            if (googleNews && dash > 0) {
                source = title.substring(dash + 3).trim();
                title = title.substring(0, dash).trim();
            }
            articles.add(new NewsArticle(title, link.trim(), source, toInstant(entry)));
        }
        return articles;
    }

    private static boolean isGoogleNews(SyndFeed feed) {
        String link = feed.getLink();
        return link != null && link.contains(GOOGLE_NEWS_HOST);
    }

    private static Instant toInstant(SyndEntry entry) {
        Date date = entry.getPublishedDate() != null ? entry.getPublishedDate() : entry.getUpdatedDate();
        return date == null ? null : date.toInstant();
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b != null && !b.isBlank() ? b : null;
    }

    static String clean(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }
}
