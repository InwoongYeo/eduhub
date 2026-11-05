package com.eduhub.global.error;

import org.springframework.http.HttpStatus;

/**
 * 애플리케이션 전체에서 쓰는 오류 코드 (NFR-03).
 */
public enum ErrorCode {

	// 공통
	INVALID_INPUT(HttpStatus.BAD_REQUEST, "G001", "입력값이 올바르지 않습니다."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "G002", "로그인이 필요합니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "G003", "접근 권한이 없습니다."),
	RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "G004", "요청한 경로를 찾을 수 없습니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "G999", "서버 오류가 발생했습니다."),

	// 강의
	COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "C001", "강의를 찾을 수 없습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

	ErrorCode(HttpStatus status, String code, String message) {
		this.status = status;
		this.code = code;
		this.message = message;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}

	public String getMessage() {
		return message;
	}
}
