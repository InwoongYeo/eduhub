package com.eduhub.enrollment.dto;

import java.time.Instant;
import java.time.LocalDateTime;

import com.eduhub.enrollment.domain.EnrollmentStatus;
import com.eduhub.enrollment.repository.MyEnrollmentRow;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 내 신청 내역의 한 건 (FR-07). 대기 순번은 저장하지 않고 조회 시 계산한 값이다 (D-36).
 */
public record MyEnrollmentResponse(
		@Schema(description = "신청 ID", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
		Long enrollmentId,
		@Schema(description = "회차 ID", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
		Long sessionId,
		@Schema(description = "과정 제목", example = "자바 기초", requiredMode = Schema.RequiredMode.REQUIRED)
		String courseTitle,
		@Schema(description = "회차 시작 일시", requiredMode = Schema.RequiredMode.REQUIRED)
		LocalDateTime sessionStartsAt,
		@Schema(description = "회차 장소", example = "본사 5층", requiredMode = Schema.RequiredMode.REQUIRED)
		String location,
		@Schema(description = "신청 상태: CONFIRMED, WAITING, CANCELLED, COMPLETED",
				requiredMode = Schema.RequiredMode.REQUIRED)
		EnrollmentStatus status,
		@Schema(description = "신청(재신청이면 마지막 신청) 시각", requiredMode = Schema.RequiredMode.REQUIRED)
		Instant appliedAt,
		@Schema(description = "WAITING일 때만 현재 대기 순번(1부터), 그 밖은 null", example = "4", nullable = true,
				requiredMode = Schema.RequiredMode.REQUIRED)
		Integer waitingNo) {

	public static MyEnrollmentResponse from(MyEnrollmentRow row) {
		return new MyEnrollmentResponse(
				row.enrollmentId(),
				row.sessionId(),
				row.courseTitle(),
				row.sessionStartsAt(),
				row.location(),
				row.status(),
				row.appliedAt(),
				row.waitingNo() == null ? null : Math.toIntExact(row.waitingNo()));
	}
}
