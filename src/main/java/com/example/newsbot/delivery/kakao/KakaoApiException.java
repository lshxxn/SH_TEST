package com.example.newsbot.delivery.kakao;

/**
 * 카카오 API 가 오류를 돌려준 경우. 응답 본문을 그대로 담아 원인을 찾기 쉽게 한다.
 */
public class KakaoApiException extends RuntimeException {

    private final int status;
    private final String responseBody;

    public KakaoApiException(String message, int status, String responseBody) {
        super(message + " (HTTP " + status + "): " + responseBody + hint(responseBody));
        this.status = status;
        this.responseBody = responseBody;
    }

    public int status() {
        return status;
    }

    public String responseBody() {
        return responseBody;
    }

    private static String hint(String body) {
        if (body == null) {
            return "";
        }
        if (body.contains("-402")) {
            return " → '카카오톡 메시지 전송' 동의가 없습니다. 동의항목을 설정한 뒤 /kakao/login 으로 다시 로그인하세요.";
        }
        if (body.contains("KOE320") || body.contains("invalid_grant")) {
            return " → 인가 코드/리프레시 토큰이 만료됐거나 이미 사용됐습니다. /kakao/login 으로 다시 로그인하세요.";
        }
        if (body.contains("KOE010") || body.contains("invalid_client")) {
            return " → 클라이언트 시크릿이 맞지 않습니다. KAKAO_CLIENT_SECRET 을 확인하세요.";
        }
        if (body.contains("KOE006")) {
            return " → 등록되지 않은 리다이렉트 URI 입니다. REST API 키 설정에 KAKAO_REDIRECT_URI 를 등록하세요.";
        }
        return "";
    }
}
