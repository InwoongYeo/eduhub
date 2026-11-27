package com.eduhub.enrollment.repository;

import java.time.Instant;
import java.time.LocalDateTime;

import com.eduhub.enrollment.domain.EnrollmentStatus;

/**
 * 내 신청 내역 조회(FR-07)의 한 행. 엔티티가 아니라 조회 전용 프로젝션이다.
 * {@code waitingNo}는 WAITING일 때만 채워지는 현재 대기 순번(1부터), 그 밖은 null.
 */
public record MyEnrollmentRow(
		Long enrollmentId,
		Long sessionId,
		String courseTitle,
		LocalDateTime sessionStartsAt,
		String location,
		EnrollmentStatus status,
		Instant appliedAt,
		Long waitingNo) {
}
