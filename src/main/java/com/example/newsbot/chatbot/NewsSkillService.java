package com.example.newsbot.chatbot;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.example.newsbot.config.NewsBotProperties;
import com.example.newsbot.news.NewsArticle;
import com.example.newsbot.news.NewsCollector;
import com.example.newsbot.news.RssParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * 친구가 분야(블록)를 고르면 그 순간 뉴스를 가져와 기사 5개를 카드로 답한다.
 * 같은 분야를 짧은 시간 안에 다시 물으면 방금 가져온 결과를 쓰고,
 * 뉴스 서버가 느리거나 실패하면 마지막으로 가져온 결과라도 보여 준다.
 */
public class NewsSkillService {

    private static final Logger log = LoggerFactory.getLogger(NewsSkillService.class);

    static final String MENU_TEXT = "어떤 분야 뉴스를 볼까요?\n아래 버튼을 누르거나 분야 이름을 보내 주세요.";
    static final String FAILED_TEXT = "지금 뉴스를 가져오지 못했어요. 잠시 후 다시 시도해 주세요.";

    private final NewsBotProperties properties;
    private final ChatbotProperties chatbot;
    private final Clock clock;
    private final RestClient restClient;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(List<NewsArticle> articles, Instant fetchedAt) {
    }

    public NewsSkillService(NewsBotProperties properties, ChatbotProperties chatbot, Clock clock, RestClient restClient) {
        this.properties = properties;
        this.chatbot = chatbot;
        this.clock = clock;
        this.restClient = restClient;
    }

    /** 카카오 5초 제한에 맞춰 연결·응답 대기 시간을 짧게 잡은 HTTP 클라이언트 */
    public static RestClient createRestClient(RestClient.Builder builder, ChatbotProperties chatbot) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(chatbot.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(chatbot.readTimeout());
        return builder.clone().requestFactory(factory).build();
    }

    public Map<String, Object> reply(JsonNode payload) {
        List<String> categories = categoryNames();
        Optional<NewsBotProperties.Section> section = resolve(payload);
        if (section.isEmpty()) {
            return KakaoSkillResponses.text(MENU_TEXT, categories);
        }
        NewsBotProperties.Section s = section.get();
        List<NewsArticle> articles;
        try {
            articles = articles(s);
        } catch (RuntimeException e) {
            log.warn("'{}' 뉴스를 가져오지 못했습니다: {}", s.displayName(), e.getMessage());
            return KakaoSkillResponses.text(FAILED_TEXT, categories);
        }
        if (articles.isEmpty()) {
            return KakaoSkillResponses.text("'" + s.displayName() + "' 분야에 새 기사가 없어요.", categories);
        }
        return KakaoSkillResponses.listCard(header(s.displayName()), articles, chatbot.maxItems(),
                moreUrl(s), categories, properties.schedule().zone());
    }

    /**
     * 어떤 분야를 골랐는지 찾는다.
     * 순서: 블록 파라미터(category) → 친구가 보낸 말 → 실행된 블록 이름.
     */
    Optional<NewsBotProperties.Section> resolve(JsonNode payload) {
        if (payload == null) {
            return Optional.empty();
        }
        for (String candidate : java.util.Arrays.asList( // 비어 있는 항목(null)이 섞일 수 있다
                text(payload.path("action").path("params").path("category")),
                text(payload.path("action").path("clientExtra").path("category")),
                text(payload.path("userRequest").path("utterance")),
                text(payload.path("userRequest").path("block").path("name")),
                text(payload.path("intent").path("name")))) {
            Optional<NewsBotProperties.Section> found = match(candidate);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    Optional<NewsBotProperties.Section> match(String said) {
        String normalized = said == null ? "" : said.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return properties.news().sections().stream()
                .filter(s -> keywords(s.displayName()).stream().anyMatch(normalized::contains))
                .findFirst();
    }

    /** 보낸 말이 분야 이름 그대로인지 ("정치", "IT", "주요 뉴스"). 분야 버튼을 눌렀을 때가 여기에 해당한다. */
    Optional<NewsBotProperties.Section> exact(String said) {
        String normalized = said == null ? "" : said.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return properties.news().sections().stream()
                .filter(s -> normalized.equals(s.displayName().replaceAll("\\s+", "").toLowerCase(Locale.ROOT))
                        || keywords(s.displayName()).contains(normalized))
                .findFirst();
    }

    private static List<String> keywords(String name) {
        return java.util.Arrays.stream(name.split("[·\\s/,]+"))
                .map(t -> t.trim().toLowerCase(Locale.ROOT))
                .filter(t -> !t.isEmpty() && !t.equals("뉴스"))
                .toList();
    }

    List<NewsArticle> articles(NewsBotProperties.Section section) {
        String key = section.displayName();
        Instant now = clock.instant();
        Cached cached = cache.get(key);
        if (cached != null && cached.fetchedAt().plus(chatbot.cacheTtl()).isAfter(now)) {
            return cached.articles();
        }
        long started = System.nanoTime();
        try {
            byte[] body = restClient.get().uri(section.feedUri()).retrieve().body(byte[].class);
            if (body == null || body.length == 0) {
                throw new IllegalStateException("빈 응답");
            }
            List<NewsArticle> picked = NewsCollector.pick(RssParser.parse(body),
                    now.minus(properties.news().lookback()), chatbot.maxItems(), new HashSet<>());
            cache.put(key, new Cached(picked, now));
            log.info("'{}' 뉴스 {}건 가져옴 ({}ms)", key, picked.size(), (System.nanoTime() - started) / 1_000_000);
            return picked;
        } catch (RuntimeException e) {
            if (cached != null) {
                log.warn("'{}' 새로 가져오기 실패, 이전 결과로 답합니다: {}", key, e.getMessage());
                return cached.articles();
            }
            throw e;
        }
    }

    List<String> categoryNames() {
        return properties.news().sections().stream().map(NewsBotProperties.Section::displayName).toList();
    }

    private static String header(String name) {
        return "📰 " + (name.endsWith("뉴스") ? name : name + " 뉴스");
    }

    /** 구글 뉴스 RSS 주소면 같은 내용의 웹 페이지 주소로 바꿔 "더보기" 링크로 쓴다. */
    String moreUrl(NewsBotProperties.Section section) {
        URI uri = section.feedUri();
        if ("news.google.com".equals(uri.getHost()) && uri.getRawPath() != null && uri.getRawPath().startsWith("/rss")) {
            String path = uri.getRawPath().substring("/rss".length());
            return "https://news.google.com" + (path.isEmpty() ? "/" : path)
                    + (uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "");
        }
        return properties.news().moreLink();
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asString();
    }
}
