package com.example.newsbot.delivery;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * 메시지 문구 공통 도우미.
 */
public final class MessageText {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private MessageText() {
    }

    /** 예: "10월 24일(토)" */
    public static String dateLabel(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return "%d월 %d일(%s)".formatted(date.getMonthValue(), date.getDayOfMonth(),
                dow.getDisplayName(TextStyle.SHORT, Locale.KOREAN));
    }

    /** 예: "연합뉴스 · 08:12" — 값이 없으면 있는 것만 */
    public static String sourceLine(String source, Instant publishedAt, ZoneId zone) {
        String time = publishedAt == null ? null : TIME.format(publishedAt.atZone(zone));
        if (source != null && !source.isBlank() && time != null) {
            return source + " · " + time;
        }
        if (source != null && !source.isBlank()) {
            return source;
        }
        return time;
    }

    /** 코드 포인트 기준으로 자르고 말줄임표를 붙인다. */
    public static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        if (s.codePointCount(0, s.length()) <= max) {
            return s;
        }
        int end = s.offsetByCodePoints(0, Math.max(0, max - 1));
        return s.substring(0, end).stripTrailing() + "…";
    }
}
