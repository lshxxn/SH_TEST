package com.example.newsbot.ai;

/**
 * 대화 한 마디. role 은 제미나이 형식("user" 또는 "model")을 따른다.
 */
public record ChatTurn(String role, String text) {

    public static ChatTurn user(String text) {
        return new ChatTurn("user", text);
    }

    public static ChatTurn model(String text) {
        return new ChatTurn("model", text);
    }
}
