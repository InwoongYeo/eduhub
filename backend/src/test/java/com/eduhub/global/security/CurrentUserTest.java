package com.eduhub.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class CurrentUserTest {

	@Test
	void JWT_subject를_사용자_id로_변환한다() {
		Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256").subject("42").build();

		assertThat(CurrentUser.from(jwt).employeeId()).isEqualTo(42L);
	}
}
