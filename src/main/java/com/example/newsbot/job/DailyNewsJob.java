package com.example.newsbot.job;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.newsbot.delivery.NewsSender;
import com.example.newsbot.news.NewsCollector;
import com.example.newsbot.news.NewsDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 정해진 시각(기본: 한국 시간 오전 9시)에 뉴스를 모아 켜져 있는 모든 발송 채널로 보낸다.
 */
@Component
@ConditionalOnProperty(name = "newsbot.site.enabled", havingValue = "false", matchIfMissing = true)
public class DailyNewsJob {

    private static final Logger log = LoggerFactory.getLogger(DailyNewsJob.class);

    private final NewsCollector collector;
    private final List<NewsSender> senders;

    public DailyNewsJob(NewsCollector collector, List<NewsSender> senders) {
        this.collector = collector;
        this.senders = senders;
        if (senders.isEmpty()) {
            log.warn("켜져 있는 발송 채널이 없습니다. KAKAO_ENABLED 또는 SOLAPI_ENABLED 를 확인하세요.");
        } else {
            log.info("발송 채널: {}", senders.stream().map(NewsSender::name).toList());
        }
    }

    public record RunResult(LocalDate date, int sections, int articles, Map<String, String> deliveries) {
    }

    @Scheduled(cron = "${newsbot.schedule.cron:0 0 9 * * *}", zone = "${newsbot.schedule.zone:Asia/Seoul}")
    public void runScheduled() {
        log.info("정기 뉴스 발송 시작");
        run();
    }

    public NewsDigest preview() {
        return collector.collect();
    }

    /** 정기 실행과 수동 실행이 겹치지 않도록 동기화한다. */
    public synchronized RunResult run() {
        NewsDigest digest = collector.collect();
        Map<String, String> deliveries = new LinkedHashMap<>();
        if (digest.isEmpty()) {
            log.warn("수집된 기사가 없어 발송하지 않습니다.");
            return new RunResult(digest.date(), 0, 0, deliveries);
        }
        for (NewsSender sender : senders) {
            try {
                sender.send(digest);
                deliveries.put(sender.name(), "OK");
            } catch (RuntimeException e) {
                log.error("[{}] 발송 실패: {}", sender.name(), e.getMessage(), e);
                deliveries.put(sender.name(), "FAILED: " + e.getMessage());
            }
        }
        return new RunResult(digest.date(), digest.sections().size(), digest.articleCount(), deliveries);
    }
}
