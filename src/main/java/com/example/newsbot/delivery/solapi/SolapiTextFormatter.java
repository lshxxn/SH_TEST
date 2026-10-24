package com.example.newsbot.delivery.solapi;

import com.example.newsbot.delivery.MessageText;
import com.example.newsbot.news.NewsArticle;
import com.example.newsbot.news.NewsDigest;
import com.example.newsbot.news.NewsSection;

/**
 * 브랜드 메시지 텍스트형 본문을 만든다. 최대 길이를 넘지 않는 만큼만 기사를 담는다.
 */
public final class SolapiTextFormatter {

    private SolapiTextFormatter() {
    }

    public record Result(String text, int includedArticles) {
    }

    public static Result format(NewsDigest digest, boolean includeLinks, int maxLength) {
        StringBuilder text = new StringBuilder("📰 ").append(MessageText.dateLabel(digest.date())).append(" 아침 뉴스");
        int included = 0;
        outer:
        for (NewsSection section : digest.sections()) {
            String sectionHeader = "\n\n[" + section.name() + "]";
            boolean headerWritten = false;
            int no = 1;
            for (NewsArticle a : section.articles()) {
                StringBuilder item = new StringBuilder();
                if (!headerWritten) {
                    item.append(sectionHeader);
                }
                item.append('\n').append(no).append(". ").append(a.title());
                if (a.source() != null && !a.source().isBlank()) {
                    item.append(" (").append(a.source()).append(')');
                }
                if (includeLinks) {
                    item.append('\n').append(a.link());
                }
                if (length(text) + length(item) > maxLength) {
                    break outer;
                }
                text.append(item);
                headerWritten = true;
                no++;
                included++;
            }
        }
        return new Result(text.toString(), included);
    }

    private static int length(CharSequence s) {
        return Character.codePointCount(s, 0, s.length());
    }
}
