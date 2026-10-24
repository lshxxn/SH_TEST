package com.example.newsbot.ai;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 친구별 최근 대화를 메모리에 잠깐 기억한다. 프로그램을 끄면 사라진다.
 */
public class ChatMemory {

    private final int maxTurns;
    private final Duration ttl;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    private static final class Session {
        final Deque<ChatTurn> turns = new ArrayDeque<>();
        Instant lastSeen;
    }

    public ChatMemory(int maxTurns, Duration ttl) {
        this.maxTurns = Math.max(0, maxTurns);
        this.ttl = ttl;
    }

    public List<ChatTurn> history(String userId, Instant now) {
        Session session = sessions.get(userId);
        if (session == null) {
            return List.of();
        }
        synchronized (session) {
            if (session.lastSeen.plus(ttl).isBefore(now)) {
                sessions.remove(userId, session);
                return List.of();
            }
            return List.copyOf(session.turns);
        }
    }

    public void remember(String userId, String question, String answer, Instant now) {
        if (maxTurns == 0) {
            return;
        }
        Session session = sessions.computeIfAbsent(userId, id -> new Session());
        synchronized (session) {
            session.turns.addLast(ChatTurn.user(question));
            session.turns.addLast(ChatTurn.model(answer));
            while (session.turns.size() > maxTurns * 2) {
                session.turns.removeFirst();
            }
            session.lastSeen = now;
        }
        if (sessions.size() > 1000) {
            sessions.entrySet().removeIf(e -> e.getValue().lastSeen != null && e.getValue().lastSeen.plus(ttl).isBefore(now));
        }
    }

    public void forget(String userId) {
        sessions.remove(userId);
    }
}
