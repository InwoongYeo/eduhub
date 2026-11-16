package com.eduhub.global.health;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서버가 떠 있는지 확인하는 API (2회차 1주차 범위). 로그인 없이 호출할 수 있다 (D-33).
 * DB 등 의존 시스템 상태는 보지 않는다.
 */
@Tag(name = "Health", description = "서버 상태 확인")
@RestController
@RequestMapping("/api/v1")
public class HealthController {

	@Operation(summary = "서버 상태 확인", description = "서버가 요청을 받을 수 있으면 {\"status\": \"UP\"}을 돌려준다")
	@GetMapping("/health")
	public Map<String, String> health() {
		return Map.of("status", "UP");
	}
}
