package com.example.newsbot.delivery.solapi;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 솔라피 브랜드 메시지(카카오톡 채널 발송) 설정 ({@code newsbot.solapi.*}).
 */
@ConfigurationProperties(prefix = "newsbot.solapi")
public record SolapiProperties(
        @DefaultValue("false") boolean enabled,
        String apiKey,
        String apiSecret,
        /** 솔라피에 연동한 비즈니스 채널의 pfId (KA01PF...) */
        String pfId,
        /** 솔라피에 등록한 발신번호. 문자 대체발송을 켤 때 필요 */
        String from,
        /** 받는 사람 휴대폰 번호 목록 (채널 친구) */
        @DefaultValue List<String> recipients,
        /** I: 채널 친구 대상. M/N 은 카카오 인허가 채널만 사용 가능 */
        @DefaultValue("I") String targeting,
        /** true 면 카카오톡 실패 시 문자로 대체 발송하지 않는다 */
        @DefaultValue("true") boolean disableSms,
        /** 본문에 기사 링크를 넣을지 여부 */
        @DefaultValue("true") boolean includeLinks,
        /** 브랜드 메시지 텍스트형 본문 최대 길이 */
        @DefaultValue("1300") int maxTextLength,
        @DefaultValue("https://api.solapi.com") String baseUrl) {
}
