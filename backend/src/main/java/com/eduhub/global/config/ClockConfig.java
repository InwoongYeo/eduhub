package com.eduhub.global.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시각 계산용 Clock 빈. 회차 시각(starts_at)이 서울 기준 LocalDateTime이므로 같은 시간대로 비교한다.
 * 테스트에서는 고정 Clock으로 바꿔 쓴다.
 */
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.system(ZoneId.of("Asia/Seoul"));
	}
}
