package com.example.newsbot.config;

import java.net.http.HttpClient;
import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class HttpClientConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * 모든 외부 호출(RSS, 카카오, 솔라피)이 공유하는 RestClient.Builder 원형.
     * 각 컴포넌트는 {@code builder.clone()} 으로 복제해서 baseUrl 등을 따로 지정한다.
     */
    @Bean
    RestClient.Builder newsBotRestClientBuilder(NewsBotProperties properties) {
        NewsBotProperties.Http http = properties.http();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(http.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(http.readTimeout());
        return RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, http.userAgent());
    }
}
