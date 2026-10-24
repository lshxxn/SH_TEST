package com.example.newsbot.delivery;

import com.example.newsbot.news.NewsDigest;

/**
 * 하루치 뉴스를 어딘가로 보내는 채널. 구현체는 설정으로 켜고 끈다.
 */
public interface NewsSender {

    /** 로그·결과 표시에 쓰는 이름 */
    String name();

    void send(NewsDigest digest);
}
