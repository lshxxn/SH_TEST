package com.example.newsbot.ai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 제미나이 연결 상태를 확인하고, 문제가 있으면 무엇을 고쳐야 하는지 알려 준다.
 * 프로그램 시작 화면과 브라우저 상태 페이지(/kakao/skill/chat)에서 쓴다.
 */
public class GeminiStatus {

    public enum State { OK, NO_KEY, FAILED }

    public record Result(State state, String message) {
    }

    private static final Duration RECHECK_AFTER = Duration.ofSeconds(30);
    /** 시험 응답이 이보다 느리면 경고 */
    private static final long SLOW_MS = 3000;
    private static final Pattern FILLED_KEY = Pattern.compile("(?m)^\\s*GEMINI_API_KEY\\s*=\\s*\\S+");

    private final GeminiProperties properties;
    private final GeminiClient client;
    private final Path workingDir;
    private final Clock clock;
    private volatile Result last;
    private volatile Instant checkedAt;

    public GeminiStatus(GeminiProperties properties, GeminiClient client, Path workingDir, Clock clock) {
        this.properties = properties;
        this.client = client;
        this.workingDir = workingDir.toAbsolutePath().normalize();
        this.clock = clock;
    }

    /** 최근 30초 안에 확인했으면 그 결과를, 아니면 다시 확인한다. */
    public Result current() {
        Instant at = checkedAt;
        if (last != null && at != null && at.plus(RECHECK_AFTER).isAfter(clock.instant())) {
            return last;
        }
        return check();
    }

    public synchronized Result check() {
        Result result;
        if (!properties.configured()) {
            result = new Result(State.NO_KEY, "키가 없습니다. " + secretsFileHint());
        } else {
            try {
                String model = client.checkModel();
                long ms = client.warmUp();
                String note = ms > SLOW_MS
                        ? " ← 느립니다. 카카오 5초 제한에 걸릴 수 있으니 secrets.properties 에 GEMINI_MODEL=<더 빠른 모델> 을 넣어 보세요"
                        : "";
                result = new Result(State.OK, "연결 확인 완료: " + model + " (" + properties.modelName() + "), 시험 응답 "
                        + String.format(java.util.Locale.ROOT, "%.1f", ms / 1000.0) + "초, 생각 단계: " + client.thinkingSetting() + note);
            } catch (GeminiException e) {
                result = new Result(State.FAILED, "연결 확인 실패: " + reasonText(e));
            }
        }
        last = result;
        checkedAt = clock.instant();
        return result;
    }

    private static String reasonText(GeminiException e) {
        return switch (e.reason()) {
            case BAD_KEY -> "키가 올바르지 않습니다. secrets.properties 의 GEMINI_API_KEY 값을 다시 복사해 넣으세요.";
            case MODEL_NOT_FOUND -> "모델 이름을 찾을 수 없습니다. secrets.properties 에 GEMINI_MODEL 을 지우거나 고치세요.";
            case RATE_LIMITED -> "무료 사용량을 넘었습니다. 잠시 후 다시 확인됩니다.";
            case TIMEOUT -> "구글 서버에 연결하지 못했습니다. 인터넷 연결이나 방화벽을 확인하세요.";
            default -> e.getMessage();
        };
    }

    /** 키 파일을 어디서 찾았는지, 흔한 실수가 있는지 알려 준다. 키 값은 절대 보여 주지 않는다. */
    String secretsFileHint() {
        Path secrets = workingDir.resolve("secrets.properties");
        Path txt = workingDir.resolve("secrets.properties.txt");
        Path example = workingDir.resolve("secrets.properties.example");
        if (Files.exists(secrets) || Files.exists(txt)) {
            return "파일(" + (Files.exists(secrets) ? secrets : txt) + ")은 있지만 GEMINI_API_KEY= 뒤에 키가 비어 있습니다.";
        }
        if (filled(example)) {
            return "secrets.properties.example 에 키를 넣으셨네요. 그 파일을 복사해서 이름을 secrets.properties 로 바꿔 주세요.";
        }
        for (Path candidate : List.of(workingDir.resolve("secrets.properties.txt.txt"), workingDir.resolve("secrets.txt"))) {
            if (Files.exists(candidate)) {
                return "파일 이름이 " + candidate.getFileName() + " 입니다. secrets.properties 로 바꿔 주세요.";
            }
        }
        return "이 위치에 secrets.properties 파일을 만드세요: " + secrets;
    }

    private static boolean filled(Path file) {
        try {
            return Files.exists(file) && FILLED_KEY.matcher(Files.readString(file)).find();
        } catch (Exception e) {
            return false;
        }
    }
}
