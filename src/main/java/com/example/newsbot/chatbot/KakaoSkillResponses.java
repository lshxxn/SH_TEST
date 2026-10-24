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

    // 카카오 챗봇 관리자센터 리스트형 말풍선 규격. 넘으면 "말풍선 포맷이 올바르지 않습니다" 로 거부된다.
    static final int LIST_CARD_MAX_ITEMS = 5;
    static final int HEADER_MAX = 15;
    static final int ITEM_TITLE_MAX = 35;            // 설명이 없을 때
    static final int ITEM_TITLE_WITH_DESC_MAX = 30;  // 설명이 있을 때
    static final int ITEM_DESC_MAX = 16;
    static final int BUTTON_LABEL_MAX = 14;
    static final int QUICK_REPLY_MAX = 10;
    static final int QUICK_REPLY_LABEL_MAX = 14;

    private KakaoSkillResponses() {
    }

    static Map<String, Object> listCard(String headerTitle, List<NewsArticle> articles, int maxItems,
                                        String moreUrl, List<String> categories, ZoneId zone) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (NewsArticle a : articles.subList(0, Math.min(articles.size(), Math.min(maxItems, LIST_CARD_MAX_ITEMS)))) {
            Map<String, Object> item = new LinkedHashMap<>();
            String description = shortMeta(a, zone);
            item.put("title", MessageText.truncate(a.title(), description == null ? ITEM_TITLE_MAX : ITEM_TITLE_WITH_DESC_MAX));
            if (description != null) {
                item.put("description", description);
            }
            item.put("link", Map.of("web", a.link()));
            items.add(item);
        }
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("header", Map.of("title", MessageText.truncate(headerTitle, HEADER_MAX)));
        card.put("items", items);
        if (moreUrl != null) {
            card.put("buttons", List.of(Map.of("label", "더보기", "action", "webLink", "webLinkUrl", moreUrl)));
        }
        return response(List.of(Map.of("listCard", card)), categories);
    }

    /** "언론사 · 08:12" 가 16자를 넘으면 언론사 이름만, 그것도 길면 잘라서 */
    static String shortMeta(NewsArticle a, ZoneId zone) {
        String full = MessageText.sourceLine(a.source(), a.publishedAt(), zone);
        if (full == null) {
            return null;
        }
        if (full.codePointCount(0, full.length()) <= ITEM_DESC_MAX) {
            return full;
        }
        String source = a.source() == null || a.source().isBlank()
                ? MessageText.sourceLine(null, a.publishedAt(), zone) : a.source();
        return MessageText.truncate(source, ITEM_DESC_MAX);
    }

    static Map<String, Object> text(String text, List<String> categories) {
        return response(List.of(Map.of("simpleText", Map.of("text", text))), categories);
    }

    private static Map<String, Object> response(List<Map<String, Object>> outputs, List<String> categories) {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("outputs", outputs);
        if (!categories.isEmpty()) {
            template.put("quickReplies", categories.stream().limit(QUICK_REPLY_MAX)
                    .map(c -> Map.of("label", MessageText.truncate(c, QUICK_REPLY_LABEL_MAX), "action", "message", "messageText", c))
                    .toList());
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("version", "2.0");
        response.put("template", template);
        return response;
    }
}
