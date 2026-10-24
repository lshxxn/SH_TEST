package com.example.newsbot.delivery.kakao;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.example.newsbot.delivery.NewsSender;
import com.example.newsbot.news.NewsDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * 카카오톡 "나에게 보내기" (POST /v2/api/talk/memo/default/send).
 * 로그인한 본인의 '나와의 채팅'으로 메시지가 온다. 무료이고 사업자 등록이 필요 없다.
 */
public class KakaoMemoSender implements NewsSender {

    private static final Logger log = LoggerFactory.getLogger(KakaoMemoSender.class);
    static final String MEMO_PATH = "/v2/api/talk/memo/default/send";

    private final KakaoTokenService tokenService;
    private final KakaoTemplateFactory templateFactory;
    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    public KakaoMemoSender(KakaoProperties properties, KakaoTokenService tokenService,
                           KakaoTemplateFactory templateFactory, RestClient.Builder builder, JsonMapper jsonMapper) {
        this.tokenService = tokenService;
        this.templateFactory = templateFactory;
        this.restClient = builder.clone().baseUrl(properties.apiBaseUrl()).build();
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String name() {
        return "kakao-memo";
    }

    @Override
    public void send(NewsDigest digest) {
        List<Map<String, Object>> templates = templateFactory.build(digest);
        for (Map<String, Object> template : templates) {
            String json = jsonMapper.writeValueAsString(template);
            try {
                post(tokenService.validAccessToken(), json);
            } catch (KakaoApiException e) {
                if (e.status() != 401) {
                    throw e;
                }
                log.info("액세스 토큰이 거부되어 갱신 후 재시도합니다.");
                post(tokenService.forceRefresh(), json);
            }
        }
        log.info("카카오 나에게 보내기 완료: 메시지 {}개, 기사 {}건", templates.size(), digest.articleCount());
    }

    private void post(String accessToken, String templateJson) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("template_object", templateJson);
        restClient.post()
                .uri(MEMO_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(KakaoOAuthClient.FORM_UTF8)
                .body(form)
                .retrieve()
                .onStatus(status -> status.isError(), (req, res) -> {
                    throw new KakaoApiException("메시지 전송 실패", res.getStatusCode().value(),
                            new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8));
                })
                .toBodilessEntity();
    }
}
