package com.example.newsbot.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 매일 정해진 시각 발송. 페이지 생성 모드(site 프로필)에서는 끈다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "newsbot.schedule.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
