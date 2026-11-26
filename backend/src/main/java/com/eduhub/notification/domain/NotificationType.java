package com.eduhub.notification.domain;

/**
 * 알림 종류 (FR-09).
 */
public enum NotificationType {

	/** 신청이 바로 확정되었다 (재신청 포함). */
	ENROLLMENT_CONFIRMED,

	/** 대기 중이던 신청이 확정으로 승급되었다. */
	WAITLIST_PROMOTED
}
