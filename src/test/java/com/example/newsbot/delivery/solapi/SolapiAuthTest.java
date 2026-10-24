package com.example.newsbot.delivery.solapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

class SolapiAuthTest {

    @Test
    void 서명은_date와_salt를_이어붙인_값의_HMAC_SHA256_hex다() {
        // 기대값: printf '%s' "2026-10-24T09:00:00+09:00abcdefghijklmnopqrstuvwxyz123456" | openssl dgst -sha256 -hmac test-secret
        String header = SolapiAuth.header("test-key", "test-secret",
                OffsetDateTime.parse("2026-10-24T09:00:00.123+09:00"), "abcdefghijklmnopqrstuvwxyz123456");

        assertThat(header).isEqualTo("HMAC-SHA256 apiKey=test-key, date=2026-10-24T09:00:00+09:00, "
                + "salt=abcdefghijklmnopqrstuvwxyz123456, "
                + "signature=856a34536c7a0d88726ca934bae25d3a17d46ce156f24125fd915ea4d12c3868");
    }

    @Test
    void salt는_매번_32자_영숫자로_새로_만든다() {
        OffsetDateTime now = OffsetDateTime.parse("2026-10-24T09:00:00+09:00");
        String a = SolapiAuth.header("k", "s", now);
        String b = SolapiAuth.header("k", "s", now);
        assertThat(a).isNotEqualTo(b).containsPattern("salt=[0-9a-zA-Z]{32},");
    }
}
