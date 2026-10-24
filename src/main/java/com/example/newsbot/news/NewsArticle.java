package com.example.newsbot.news;

import java.time.Instant;

/**
 * 기사 한 건. 본문은 저장·전송하지 않고 제목과 원문 링크만 다룬다.
 *
 * @param title       기사 제목 (언론사 접미사 제거)
 * @param link        원문 링크
 * @param source      언론사 또는 피드 이름 (없을 수 있음)
 * @param publishedAt 발행 시각 (없을 수 있음)
 */
public record NewsArticle(String title, String link, String source, Instant publishedAt) {
}
