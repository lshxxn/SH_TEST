package com.example.newsbot.web;

import com.example.newsbot.job.DailyNewsJob;
import com.example.newsbot.news.NewsDigest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 테스트용 수동 실행 API. 헤더 X-Admin-Token 필요.
 */
@RestController
@RequestMapping("/api/news")
public class AdminController {

    private final DailyNewsJob job;
    private final AdminGuard adminGuard;

    public AdminController(DailyNewsJob job, AdminGuard adminGuard) {
        this.job = job;
        this.adminGuard = adminGuard;
    }

    /** 수집만 하고 보내지 않는다. 피드 설정 확인용. */
    @GetMapping("/preview")
    public NewsDigest preview(@RequestHeader(name = "X-Admin-Token", required = false) String token) {
        adminGuard.check(token);
        return job.preview();
    }

    /** 지금 바로 수집해서 발송한다. */
    @PostMapping("/send-now")
    public DailyNewsJob.RunResult sendNow(@RequestHeader(name = "X-Admin-Token", required = false) String token) {
        adminGuard.check(token);
        return job.run();
    }
}
