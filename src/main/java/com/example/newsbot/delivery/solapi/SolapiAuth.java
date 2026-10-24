package com.example.newsbot.delivery.solapi;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 솔라피 API 인증 헤더.
 * {@code HMAC-SHA256 apiKey=.., date=.., salt=.., signature=hex(HMAC-SHA256(secret, date + salt))}
 */
public final class SolapiAuth {

    private static final String SALT_ALPHABET = "1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private SolapiAuth() {
    }

    public static String header(String apiKey, String apiSecret, OffsetDateTime now) {
        return header(apiKey, apiSecret, now, randomSalt());
    }

    static String header(String apiKey, String apiSecret, OffsetDateTime now, String salt) {
        String date = now.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String signature = hmacSha256Hex(apiSecret, date + salt);
        return "HMAC-SHA256 apiKey=" + apiKey + ", date=" + date + ", salt=" + salt + ", signature=" + signature;
    }

    static String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String randomSalt() {
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) {
            sb.append(SALT_ALPHABET.charAt(RANDOM.nextInt(SALT_ALPHABET.length())));
        }
        return sb.toString();
    }
}
