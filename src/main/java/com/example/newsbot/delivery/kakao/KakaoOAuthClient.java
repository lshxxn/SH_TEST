package com.example.newsbot.delivery.kakao;

import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 카카오 로그인 REST API (인가 코드 → 토큰 발급, 토큰 갱신).
 */
public class KakaoOAuthClient {

    /** 카카오톡 메시지 전송 동의항목 ID */
    static final String TALK_MESSAGE_SCOPE = "talk_message";
    static final MediaType FORM_UTF8 = MediaType.parseMediaType("application/x-www-form-urlencoded;charset=utf-8");

    private final KakaoProperties properties;
    private final RestClient restClient;

    public KakaoOAuthClient(KakaoProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.clone().baseUrl(properties.authBaseUrl()).build();
    }

    public URI authorizeUri(String state) {
        return UriComponentsBuilder.fromUriString(properties.authBaseUrl())
                .path("/oauth/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", requireRestApiKey())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("scope", TALK_MESSAGE_SCOPE)
                .queryParam("state", state)
                .encode()
                .build()
                .toUri();
    }

    public KakaoTokenResponse issueToken(String authorizationCode) {
        MultiValueMap<String, String> form = baseForm("authorization_code");
        form.add("redirect_uri", properties.redirectUri());
        form.add("code", authorizationCode);
        return postToken(form);
    }

    public KakaoTokenResponse refresh(String refreshToken) {
        MultiValueMap<String, String> form = baseForm("refresh_token");
        form.add("refresh_token", refreshToken);
        return postToken(form);
    }

    private MultiValueMap<String, String> baseForm(String grantType) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", grantType);
        form.add("client_id", requireRestApiKey());
        if (StringUtils.hasText(properties.clientSecret())) {
            form.add("client_secret", properties.clientSecret());
        }
        return form;
    }

    private KakaoTokenResponse postToken(MultiValueMap<String, String> form) {
        KakaoTokenResponse response = restClient.post()
                .uri("/oauth/token")
                .contentType(FORM_UTF8)
                .body(form)
                .retrieve()
                .onStatus(status -> status.isError(), (req, res) -> {
                    throw new KakaoApiException("토큰 요청 실패", res.getStatusCode().value(),
                            new String(res.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                })
                .body(KakaoTokenResponse.class);
        if (response == null || !StringUtils.hasText(response.accessToken())) {
            throw new KakaoApiException("토큰 응답에 access_token 이 없습니다", 200, String.valueOf(response));
        }
        return response;
    }

    private String requireRestApiKey() {
        if (!StringUtils.hasText(properties.restApiKey())) {
            throw new IllegalStateException("KAKAO_REST_API_KEY 가 설정되지 않았습니다.");
        }
        return properties.restApiKey();
    }
}
