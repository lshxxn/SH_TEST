package com.example.newsbot.delivery.kakao;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;

import tools.jackson.databind.json.JsonMapper;

/**
 * 토큰을 JSON 파일 하나에 저장한다. 서버 한 대에서 도는 개인용 봇이라 DB 는 쓰지 않는다.
 */
public class KakaoTokenStore {

    private final Path file;
    private final JsonMapper jsonMapper;

    public KakaoTokenStore(Path file, JsonMapper jsonMapper) {
        this.file = file.toAbsolutePath().normalize();
        this.jsonMapper = jsonMapper;
    }

    public Optional<KakaoToken> load() {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(jsonMapper.readValue(Files.readString(file), KakaoToken.class));
        } catch (IOException e) {
            throw new UncheckedIOException("토큰 파일을 읽지 못했습니다: " + file, e);
        }
    }

    public void save(KakaoToken token) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = Files.createTempFile(file.getParent(), ".kakao-token", ".tmp");
            restrictPermissions(tmp);
            Files.writeString(tmp, jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(token));
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("토큰 파일을 저장하지 못했습니다: " + file, e);
        }
    }

    public Path file() {
        return file;
    }

    private static void restrictPermissions(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows 등 POSIX 권한을 지원하지 않는 파일 시스템
        }
    }
}
