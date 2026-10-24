package com.example.newsbot.delivery.solapi;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.newsbot.delivery.DigestTextFormatter;
import com.example.newsbot.delivery.NewsSender;
import com.example.newsbot.news.NewsDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * 카카오톡 채널 이름으로 채널 친구에게 브랜드 메시지(자유형, 텍스트)를 보낸다.
 * 친구톡은 2026-01-01 부로 브랜드 메시지로 대체되었다.
 * 요청 형식은 솔라피 공식 SDK 의 BMS_FREE 예제와 같다.
 */
public class SolapiBrandMessageSender implements NewsSender {

    private static final Logger log = LoggerFactory.getLogger(SolapiBrandMessageSender.class);
    static final String SEND_PATH = "/messages/v4/send-many/detail";
    /** 한 번 호출에 담을 수 있는 최대 메시지 수 */
    static final int MAX_BATCH = 10_000;

    private final SolapiProperties properties;
    private final String moreLink;
    private final ZoneId zone;
    private final Clock clock;
    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    public SolapiBrandMessageSender(SolapiProperties properties, String moreLink, ZoneId zone, Clock clock,
                                    RestClient.Builder builder, JsonMapper jsonMapper) {
        this.properties = properties;
        this.moreLink = moreLink;
        this.zone = zone;
        this.clock = clock;
        this.restClient = builder.clone().baseUrl(properties.baseUrl()).build();
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String name() {
        return "solapi-brand-message";
    }

    @Override
    public void send(NewsDigest digest) {
        validateConfig();
        List<String> recipients = normalizedRecipients();
        if (recipients.isEmpty()) {
            throw new IllegalStateException("SOLAPI_RECIPIENTS 에 받는 사람 번호가 없습니다.");
        }
        DigestTextFormatter.Result body = DigestTextFormatter.format(
                digest, properties.includeLinks(), properties.maxTextLength());
        if (body.includedArticles() < digest.articleCount()) {
            log.info("본문 길이 제한({}자) 때문에 기사 {}건 중 {}건만 담았습니다.",
                    properties.maxTextLength(), digest.articleCount(), body.includedArticles());
        }

        int sent = 0;
        for (int from = 0; from < recipients.size(); from += MAX_BATCH) {
            List<String> batch = recipients.subList(from, Math.min(recipients.size(), from + MAX_BATCH));
            sent += sendBatch(batch, body.text());
        }
        log.info("브랜드 메시지 접수 완료: {}/{}명", sent, recipients.size());
    }

    private int sendBatch(List<String> recipients, String text) {
        List<Map<String, Object>> messages = new ArrayList<>();
        for (String to : recipients) {
            messages.add(message(to, text));
        }
        // 직접 직렬화해서 Content-Length 를 붙인다 (chunked 전송을 피함)
        byte[] requestBody = jsonMapper.writeValueAsBytes(Map.of("messages", messages));
        SolapiSendResponse response = restClient.post()
                .uri(SEND_PATH)
                .header(HttpHeaders.AUTHORIZATION, SolapiAuth.header(
                        properties.apiKey(), properties.apiSecret(), OffsetDateTime.now(clock.withZone(zone))))
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .onStatus(status -> status.isError(), (req, res) -> {
                    throw new IllegalStateException("솔라피 발송 요청 실패 (HTTP " + res.getStatusCode().value() + "): "
                            + new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8));
                })
                .body(SolapiSendResponse.class);

        if (response == null) {
            throw new IllegalStateException("솔라피 응답이 비어 있습니다.");
        }
        List<SolapiSendResponse.FailedMessage> failed =
                response.failedMessageList() == null ? List.of() : response.failedMessageList();
        for (SolapiSendResponse.FailedMessage f : failed) {
            log.warn("접수 실패 {}: [{}] {}", mask(f.to()), f.statusCode(), f.statusMessage());
        }
        if (!failed.isEmpty() && failed.size() >= recipients.size()) {
            throw new IllegalStateException("모든 메시지 접수 실패. 첫 오류: "
                    + failed.getFirst().statusCode() + " " + failed.getFirst().statusMessage());
        }
        return recipients.size() - failed.size();
    }

    private Map<String, Object> message(String to, String text) {
        Map<String, Object> bms = new LinkedHashMap<>();
        bms.put("targeting", properties.targeting());
        bms.put("chatBubbleType", "TEXT");
        if (StringUtils.hasText(moreLink)) {
            bms.put("buttons", List.of(Map.of(
                    "name", "뉴스 더보기",
                    "linkType", "WL",
                    "linkMobile", moreLink,
                    "linkPc", moreLink)));
        }

        Map<String, Object> kakaoOptions = new LinkedHashMap<>();
        kakaoOptions.put("pfId", properties.pfId());
        kakaoOptions.put("disableSms", properties.disableSms());
        kakaoOptions.put("bms", bms);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("to", to);
        if (StringUtils.hasText(properties.from())) {
            message.put("from", properties.from().replaceAll("\\D", ""));
        }
        message.put("text", text);
        message.put("type", "BMS_FREE");
        message.put("kakaoOptions", kakaoOptions);
        return message;
    }

    private List<String> normalizedRecipients() {
        return properties.recipients().stream()
                .map(n -> n.replaceAll("\\D", ""))
                .filter(n -> !n.isEmpty())
                .distinct()
                .toList();
    }

    private void validateConfig() {
        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(properties.apiKey())) missing.add("SOLAPI_API_KEY");
        if (!StringUtils.hasText(properties.apiSecret())) missing.add("SOLAPI_API_SECRET");
        if (!StringUtils.hasText(properties.pfId())) missing.add("SOLAPI_PF_ID");
        if (!properties.disableSms() && !StringUtils.hasText(properties.from())) missing.add("SOLAPI_FROM");
        if (!missing.isEmpty()) {
            throw new IllegalStateException("솔라피 설정 누락: " + String.join(", ", missing));
        }
    }

    static String mask(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
