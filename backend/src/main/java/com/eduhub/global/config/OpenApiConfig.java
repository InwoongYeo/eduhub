package com.eduhub.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI 문서 정보 (NFR-02). prod에서는 application-prod.yml에서 꺼진다 (D-07).
 * JWT 인증 버튼은 2주차에 추가한다 (D-06).
 */
@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI eduhubOpenApi() {
		return new OpenAPI().info(new Info()
				.title("EduHub API")
				.description("온라인 강의 수강 관리 서비스")
				.version("v1"));
	}
}
