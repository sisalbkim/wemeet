package com.kopo.wemeet.config;

import lombok.RequiredArgsConstructor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * OpenApiRestClientFactory는 애플리케이션 실행에 필요한 Spring 설정과 보안/외부 연동 옵션을 구성합니다.
 */
@Component
@RequiredArgsConstructor
public class OpenApiRestClientFactory {
    // 외부 API RestClient에 동일한 연결/응답 타임아웃 정책을 적용한다.

    private final OpenApiProperties properties;

    public RestClient create(String baseUrl) {
        return create(baseUrl, builder -> {
        });
    }

    public RestClient create(String baseUrl, Consumer<RestClient.Builder> customizer) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMillis()));

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory);

        customizer.accept(builder);
        return builder.build();
    }
}
