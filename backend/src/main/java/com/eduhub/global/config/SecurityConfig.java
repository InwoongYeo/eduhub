package com.eduhub.global.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.eduhub.global.error.ErrorCode;
import com.eduhub.global.error.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 보안 뼈대 (NFR-01). JWT 발급·검증은 2주차에 추가한다 (D-06).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private static final String[] SWAGGER_PATHS = {
			"/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**"
	};

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/error").permitAll()
						.requestMatchers(SWAGGER_PATHS).permitAll()
						// FR-03: 강의 조회·검색은 누구나
						.requestMatchers(HttpMethod.GET, "/api/courses", "/api/courses/**").permitAll()
						// 역할별 권한 틀 (D-01: 계정당 역할 하나)
						.requestMatchers("/api/admin/**").hasRole("ADMIN")
						.requestMatchers("/api/instructor/**").hasRole("INSTRUCTOR")
						.anyRequest().authenticated())
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint((request, response, e) ->
								writeError(response, ErrorCode.UNAUTHORIZED))
						.accessDeniedHandler((request, response, e) ->
								writeError(response, ErrorCode.FORBIDDEN)));
		return http.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	private static void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(ErrorResponse.of(errorCode).toJson());
	}
}
