package com.kopo.wemeet.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * ApiHealthController는 화면 요청과 API 요청을 받아 서비스 계층으로 위임하는 MVC 컨트롤러입니다.
 */
@RestController
@RequestMapping("/api")
public class ApiHealthController {
    // 애플리케이션의 기본 응답 상태를 확인하는 헬스체크 엔드포인트.

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
