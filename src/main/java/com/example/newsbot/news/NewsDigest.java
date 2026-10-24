package com.example.newsbot.news;

import java.time.LocalDate;
import java.util.List;

/**
 * 하루치 발송 단위.
 */
public record NewsDigest(LocalDate date, List<NewsSection> sections) {

    public NewsDigest {
        sections = List.copyOf(sections);
    }

    public boolean isEmpty() {
        return sections.stream().allMatch(s -> s.articles().isEmpty());
    }

    public int articleCount() {
        return sections.stream().mapToInt(s -> s.articles().size()).sum();
    }
}
