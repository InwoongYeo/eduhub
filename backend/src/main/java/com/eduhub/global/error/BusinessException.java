package com.eduhub.global.error;

/**
 * 비즈니스 규칙 위반을 나타내는 예외. GlobalExceptionHandler가 ErrorResponse로 변환한다.
 */
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}
}
