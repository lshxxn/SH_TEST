package com.example.newsbot.delivery.kakao;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.newsbot.delivery.MessageText;
import com.example.newsbot.news.NewsArticle;
import com.example.newsbot.news.NewsDigest;
import com.example.newsbot.news.NewsSection;

/**
 * 카카오톡 메시지 기본 템플릿(template_object) 을 만든다.
 * <ul>
 *   <li>리스트 템플릿: 항목 2~3개 → 섹션 기사를 3개씩 나눠 여러 메시지로 보낸다.</li>
 *   <li>텍스트 템플릿: 최대 200자 → 기사가 1건뿐인 섹션에 쓴다.</li>
 * </ul>
 * 링크 도메인은 카카오디벨로퍼스 [앱 > 제품 링크 관리 > 웹 도메인] 에 등록돼 있어야 열린다.
 */
public class KakaoTemplateFactory {

    static final int LIST_MAX_ITEMS = 3;
    static final int TEXT_MAX_LENGTH = 200;
    private static final int TITLE_MAX_LENGTH = 80;
    private static final int HEADER_MAX_LENGTH = 40;

    private final String moreLink;
    private final ZoneId zone;

    public KakaoTemplateFactory(String moreLink, ZoneId zone) {
        this.moreLink = moreLink;
        this.zone = zone;
    }

    public List<Map<String, Object>> build(NewsDigest digest) {
        String date = MessageText.dateLabel(digest.date());
        List<Map<String, Object>> templates = new ArrayList<>();
        for (NewsSection section : digest.sections()) {
            List<List<NewsArticle>> chunks = chunk(section.articles());
            for (int i = 0; i < chunks.size(); i++) {
                String header = "📰 " + date + " " + section.name()
                        + (chunks.size() > 1 ? " (" + (i + 1) + "/" + chunks.size() + ")" : "");
                List<NewsArticle> chunk = chunks.get(i);
                templates.add(chunk.size() == 1 ? textTemplate(header, chunk.getFirst()) : listTemplate(header, chunk));
            }
        }
        return templates;
    }

    private Map<String, Object> listTemplate(String header, List<NewsArticle> articles) {
        List<Map<String, Object>> contents = new ArrayList<>();
        for (NewsArticle a : articles) {
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("title", MessageText.truncate(a.title(), TITLE_MAX_LENGTH));
            String sourceLine = MessageText.sourceLine(a.source(), a.publishedAt(), zone);
            if (sourceLine != null) {
                content.put("description", sourceLine);
            }
            content.put("link", link(a.link()));
            contents.add(content);
        }
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("object_type", "list");
        template.put("header_title", MessageText.truncate(header, HEADER_MAX_LENGTH));
        template.put("header_link", link(moreLink));
        template.put("contents", contents);
        template.put("buttons", List.of(Map.of("title", "뉴스 더보기", "link", link(moreLink))));
        return template;
    }

    private Map<String, Object> textTemplate(String header, NewsArticle a) {
        StringBuilder text = new StringBuilder(header).append("\n\n").append(a.title());
        String sourceLine = MessageText.sourceLine(a.source(), a.publishedAt(), zone);
        if (sourceLine != null) {
            text.append("\n- ").append(sourceLine);
        }
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("object_type", "text");
        template.put("text", MessageText.truncate(text.toString(), TEXT_MAX_LENGTH));
        template.put("link", link(a.link()));
        template.put("button_title", "기사 보기");
        return template;
    }

    private static Map<String, String> link(String url) {
        return Map.of("web_url", url, "mobile_web_url", url);
    }

    /**
     * 리스트 템플릿은 항목이 최소 2개여야 하므로 1개짜리 묶음이 생기지 않게 나눈다.
     * 예) 4 → 2+2, 5 → 3+2, 7 → 3+2+2. 전체가 1개면 그대로 둔다(텍스트 템플릿).
     */
    static <T> List<List<T>> chunk(List<T> items) {
        List<List<T>> chunks = new ArrayList<>();
        int i = 0;
        int remaining = items.size();
        while (remaining > 0) {
            int size = remaining == 4 ? 2 : Math.min(LIST_MAX_ITEMS, remaining);
            chunks.add(List.copyOf(items.subList(i, i + size)));
            i += size;
            remaining -= size;
        }
        return chunks;
    }
}
