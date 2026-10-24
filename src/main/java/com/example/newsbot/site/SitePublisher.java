package com.example.newsbot.site;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;

import com.example.newsbot.config.NewsBotProperties;
import com.example.newsbot.news.NewsCollector;
import com.example.newsbot.news.NewsDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 뉴스를 모아 정적 페이지(index.html)와 챗봇용 데이터(news.json)를 만든다.
 * GitHub Actions 가 매일 아침 이 모드로 한 번 실행하고, 결과를 GitHub Pages 에 올린다.
 */
@Component
@ConditionalOnProperty(name = "newsbot.site.enabled", havingValue = "true")
public class SitePublisher implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SitePublisher.class);

    private final NewsCollector collector;
    private final SiteProperties site;
    private final NewsBotProperties properties;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public SitePublisher(NewsCollector collector, SiteProperties site, NewsBotProperties properties,
                         JsonMapper jsonMapper, Clock clock) {
        this.collector = collector;
        this.site = site;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        NewsDigest digest = collector.collect();
        if (digest.isEmpty()) {
            // 빈 페이지로 덮어쓰지 않도록 실패 처리한다. 어제 페이지가 그대로 남는다.
            throw new IllegalStateException("수집된 기사가 없어 페이지를 만들지 않습니다.");
        }
        publish(digest, clock.instant());
    }

    void publish(NewsDigest digest, Instant now) throws IOException {
        Path out = site.outputDir().toAbsolutePath().normalize();
        Files.createDirectories(out);
        NewsPageRenderer renderer = new NewsPageRenderer(site, properties.schedule().zone());
        Files.writeString(out.resolve("index.html"), renderer.render(digest, now));
        Files.writeString(out.resolve("news.json"), jsonMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(NewsJson.from(digest, now, site.pageUrl(), properties.schedule().zone())));
        Files.writeString(out.resolve(".nojekyll"), "");
        log.info("뉴스 페이지 생성 완료: {} (섹션 {}개, 기사 {}건)", out, digest.sections().size(), digest.articleCount());
    }
}
