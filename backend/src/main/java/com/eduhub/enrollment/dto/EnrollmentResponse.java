package com.eduhub.enrollment.dto;

import java.time.Instant;

import com.eduhub.enrollment.domain.Enrollment;
import com.eduhub.enrollment.domain.EnrollmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 수강 신청 결과 (FR-04)와 내 신청 상태 (FR-05). 대기 순번은 저장하지 않고 계산해 받는다.
 */
public record EnrollmentResponse(
		@Schema(description = "신청 ID", example = "12")
		Long enrollmentId,
		@Schema(description = "회차 ID", example = "3")
		Long sessionId,
		@Schema(description = "신청 상태: CONFIRMED, WAITING, CANCELLED, COMPLETED")
		EnrollmentStatus status,
		@Schema(description = "신청(재신청) 시각. 대기 순번의 기준 (FR-05)")
		Instant appliedAt,
		@Schema(description = "대기 순번. 대기 중일 때 1부터, 아니면 null", example = "4", nullable = true)
		Integer waitingNo) {

	public static EnrollmentResponse of(Enrollment enrollment, Integer waitingNo) {
		return new EnrollmentResponse(
				enrollment.getId(),
				enrollment.getSession().getId(),
				enrollment.getStatus(),
				enrollment.getAppliedAt(),
				waitingNo);
	}
}
