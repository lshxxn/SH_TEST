package com.example.newsbot.ai;

/**
 * 제미나이 API 오류. reason 으로 사용자에게 보여 줄 안내를 고른다.
 */
public class GeminiException extends RuntimeException {

    public enum Reason { TIMEOUT, RATE_LIMITED, BAD_KEY, MODEL_NOT_FOUND, BLOCKED, OTHER }

    private final Reason reason;

    public GeminiException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public GeminiException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    /** 카카오톡 친구에게 보여 줄 문구 */
    public String userMessage() {
        return switch (reason) {
            case TIMEOUT -> "답을 만드는 데 시간이 오래 걸리고 있어요. 질문을 조금 짧게 다시 보내 주세요.";
            case RATE_LIMITED -> "지금은 AI 사용량이 많아 잠시 쉬고 있어요. 조금 뒤에 다시 물어봐 주세요.";
            case BLOCKED -> "그 질문에는 답하기 어려워요. 다른 질문을 해 주세요.";
            case BAD_KEY, MODEL_NOT_FOUND, OTHER -> "AI 대화에 문제가 생겼어요. 잠시 후 다시 시도해 주세요.";
        };
    }
}
