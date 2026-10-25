package com.example.newsbot.chatbot;

import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 최근 오류 몇 건을 기억해 상태 페이지에 보여 준다. 터미널 로그를 못 찾아도 브라우저로 확인할 수 있게.
 */
public class RecentErrors {

    private static final int MAX = 5;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    private final Clock clock;
    private final ZoneId zone;
    private final Deque<String> entries = new ArrayDeque<>();

    public RecentErrors(Clock clock, ZoneId zone) {
        this.clock = clock;
        this.zone = zone;
    }

    public synchronized void add(String where, Throwable error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName()
                : error.getClass().getSimpleName() + ": " + error.getMessage();
        if (message.length() > 300) {
            message = message.substring(0, 300) + "…";
        }
        entries.addFirst(TIME.format(clock.instant().atZone(zone)) + " " + where + " → " + message);
        while (entries.size() > MAX) {
            entries.removeLast();
        }
    }

    public synchronized List<String> list() {
        return new ArrayList<>(entries);
    }

    /** 상태 페이지용 문장 */
    public String summary() {
        List<String> list = list();
        if (list.isEmpty()) {
            return "최근 오류: 없음";
        }
        return "최근 오류 " + list.size() + "건 (최신순):\n- " + String.join("\n- ", list);
    }
}
