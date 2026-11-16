package com.eduhub.enrollment.controller;

import com.eduhub.enrollment.dto.EnrollmentResponse;
import com.eduhub.enrollment.service.EnrollmentService;
import com.eduhub.global.error.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회차 수강 신청 (FR-04). 임직원만 호출할 수 있고 (SecurityConfig), 신청자는 토큰의 subject에서만 얻는다 (NFR-03).
 */
@Tag(name = "Enrollment", description = "수강 신청")
@RestController
@RequestMapping("/api/v1")
public class EnrollmentController {

	private final EnrollmentService enrollmentService;

	public EnrollmentController(EnrollmentService enrollmentService) {
		this.enrollmentService = enrollmentService;
	}

	@Operation(summary = "회차 수강 신청",
			description = "자리가 있으면 CONFIRMED, 정원이 차면 WAITING으로 등록한다. 신청자는 토큰에서 얻는다")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "신청 완료 (CONFIRMED 또는 WAITING)"),
			@ApiResponse(responseCode = "401", description = "인증 실패",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "임직원이 아님",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "회차가 없음",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "이미 신청했거나 신청할 수 없는 회차",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@PostMapping("/sessions/{sessionId}/enrollments")
	@ResponseStatus(HttpStatus.CREATED)
	public EnrollmentResponse enroll(@PathVariable Long sessionId, @AuthenticationPrincipal Jwt jwt) {
		return enrollmentService.enroll(sessionId, Long.valueOf(jwt.getSubject()));
	}
}
