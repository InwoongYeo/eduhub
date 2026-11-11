package com.eduhub.course.dto;

import com.eduhub.course.domain.Course;
import com.eduhub.course.domain.CourseStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 과정 목록 항목 (FR-01). 모든 필드는 항상 채워진다(OpenAPI required → 생성 클라이언트에서 non-optional).
 * sessionCount는 목록 조회 후 한 번의 집계 쿼리로 채운다 (D-24).
 */
public record CourseSummaryResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String category,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) CourseStatus status,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int sessionCount) {

	public static CourseSummaryResponse of(Course course, long sessionCount) {
		return new CourseSummaryResponse(
				course.getId(),
				course.getTitle(),
				course.getCategory(),
				course.getStatus(),
				(int) sessionCount);
	}
}
