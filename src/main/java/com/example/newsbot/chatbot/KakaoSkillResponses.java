package com.example.newsbot.chatbot;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.newsbot.delivery.MessageText;
import com.example.newsbot.news.NewsArticle;

/**
 * 카카오 i 오픈빌더 스킬 응답(version 2.0) JSON 을 만든다.
 */
final class KakaoSkillResponses {

    static final int LIST_CARD_MAX_ITEMS = 5;
    static final int QUICK_REPLY_MAX = 10;

    private KakaoSkillResponses() {
    }

    static Map<String, Object> listCard(String headerTitle, List<NewsArticle> articles, int maxItems,
                                        String moreUrl, List<String> categories, ZoneId zone) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (NewsArticle a : articles.subList(0, Math.min(articles.size(), Math.min(maxItems, LIST_CARD_MAX_ITEMS)))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("title", MessageText.truncate(a.title(), 50));
            String meta = MessageText.sourceLine(a.source(), a.publishedAt(), zone);
            if (meta != null) {
                item.put("description", MessageText.truncate(meta, 40));
            }
            item.put("link", Map.of("web", a.link()));
            items.add(item);
        }
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("header", Map.of("title", MessageText.truncate(headerTitle, 30)));
        card.put("items", items);
        if (moreUrl != null) {
            card.put("buttons", List.of(Map.of("label", "더보기", "action", "webLink", "webLinkUrl", moreUrl)));
        }
        return response(List.of(Map.of("listCard", card)), categories);
    }

    static Map<String, Object> text(String text, List<String> categories) {
        return response(List.of(Map.of("simpleText", Map.of("text", text))), categories);
    }

    private static Map<String, Object> response(List<Map<String, Object>> outputs, List<String> categories) {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("outputs", outputs);
        if (!categories.isEmpty()) {
            template.put("quickReplies", categories.stream().limit(QUICK_REPLY_MAX)
                    .map(c -> Map.of("label", MessageText.truncate(c, 14), "action", "message", "messageText", c))
                    .toList());
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("version", "2.0");
        response.put("template", template);
        return response;
    }
}
