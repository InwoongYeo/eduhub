package com.eduhub.global.security;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 인증된 사용자. JWT subject = 사용자 id (D-06).
 * 신청자 ID는 요청 파라미터가 아니라 반드시 여기서 꺼낸다 (NFR-03).
 */
public record CurrentUser(Long employeeId) {

	public static CurrentUser from(Jwt jwt) {
		return new CurrentUser(Long.valueOf(jwt.getSubject()));
	}
}
