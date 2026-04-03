package com.kopo.wemeet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WemeetApplication {
    // Spring Boot 앱 시작점이다.
    // @ConfigurationPropertiesScan 덕분에 application.properties 값을 바인딩하는 설정 클래스도 함께 등록된다.

    public static void main(String[] args) {
        SpringApplication.run(WemeetApplication.class, args);
    }

}
