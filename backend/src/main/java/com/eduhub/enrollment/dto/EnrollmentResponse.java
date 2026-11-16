package com.eduhub.enrollment.dto;

import java.time.Instant;

import com.eduhub.enrollment.domain.Enrollment;
import com.eduhub.enrollment.domain.EnrollmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 수강 신청 결과 (FR-04). 대기 순번은 FR-05에서 추가한다 (D-31).
 */
public record EnrollmentResponse(
		@Schema(description = "신청 ID", example = "12")
		Long enrollmentId,
		@Schema(description = "회차 ID", example = "3")
		Long sessionId,
		@Schema(description = "신청 상태. 자리가 있으면 CONFIRMED, 정원이 차면 WAITING (D-31)",
				allowableValues = {"CONFIRMED", "WAITING"})
		EnrollmentStatus status,
		@Schema(description = "신청(재신청) 시각. 대기 순번의 기준 (FR-05)")
		Instant appliedAt) {

	public static EnrollmentResponse from(Enrollment enrollment) {
		return new EnrollmentResponse(
				enrollment.getId(),
				enrollment.getSession().getId(),
				enrollment.getStatus(),
				enrollment.getAppliedAt());
	}
}
