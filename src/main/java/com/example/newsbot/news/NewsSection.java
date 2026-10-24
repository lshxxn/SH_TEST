package com.example.newsbot.news;

import java.util.List;

public record NewsSection(String name, List<NewsArticle> articles) {

    public NewsSection {
        articles = List.copyOf(articles);
    }
}
