package com.eduhub.enrollment.controller;

import java.util.List;

import com.eduhub.enrollment.domain.EnrollmentStatus;
import com.eduhub.enrollment.dto.EnrollmentResponse;
import com.eduhub.enrollment.dto.MyEnrollmentResponse;
import com.eduhub.enrollment.service.EnrollmentService;
import com.eduhub.enrollment.service.MyEnrollmentQueryService;
import com.eduhub.global.error.ErrorResponse;
import com.eduhub.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
	private final MyEnrollmentQueryService myEnrollmentQueryService;

	public EnrollmentController(EnrollmentService enrollmentService,
			MyEnrollmentQueryService myEnrollmentQueryService) {
		this.enrollmentService = enrollmentService;
		this.myEnrollmentQueryService = myEnrollmentQueryService;
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
		return enrollmentService.enroll(sessionId, CurrentUser.from(jwt).employeeId());
	}

	@Operation(summary = "내 신청 취소",
			description = "토큰 사용자의 해당 회차 신청을 취소한다. 확정 신청을 취소하면 대기 1순위가 자동 확정된다. 회차 시작 전까지만 취소할 수 있다")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "취소 완료 (status = CANCELLED)"),
			@ApiResponse(responseCode = "401", description = "인증 실패",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "임직원이 아님",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "회차가 없거나 신청 내역이 없음",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "409", description = "이미 취소·수료된 신청이거나 취소 기간이 지난 회차",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@PostMapping("/sessions/{sessionId}/enrollments/me/cancel")
	public EnrollmentResponse cancel(@PathVariable Long sessionId, @AuthenticationPrincipal Jwt jwt) {
		return enrollmentService.cancel(sessionId, CurrentUser.from(jwt).employeeId());
	}

	@Operation(summary = "내 신청 상태 조회",
			description = "토큰 사용자의 해당 회차 신청 상태를 돌려준다. 대기 중이면 현재 대기 순번(waitingNo)을 함께 돌려준다")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공"),
			@ApiResponse(responseCode = "401", description = "인증 실패",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "임직원이 아님",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "404", description = "회차가 없거나 신청 내역이 없음",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@GetMapping("/sessions/{sessionId}/enrollments/me")
	public EnrollmentResponse findMine(@PathVariable Long sessionId, @AuthenticationPrincipal Jwt jwt) {
		return enrollmentService.findMine(sessionId, CurrentUser.from(jwt).employeeId());
	}

	@Operation(summary = "내 신청 내역",
			description = "토큰 사용자의 신청 내역을 최신 신청순으로 돌려준다. 대기 중인 신청에는 현재 대기 순번(waitingNo)을 계산해 담는다. 페이지 없이 전체를 돌려준다")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공 (없으면 빈 배열)"),
			@ApiResponse(responseCode = "400", description = "status 값이 올바르지 않음",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "401", description = "인증 실패",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
			@ApiResponse(responseCode = "403", description = "임직원이 아님",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	@GetMapping("/me/enrollments")
	public List<MyEnrollmentResponse> myEnrollments(
			@Parameter(description = "CONFIRMED·WAITING·CANCELLED·COMPLETED, 생략 시 전체")
			@RequestParam(required = false) EnrollmentStatus status,
			@AuthenticationPrincipal Jwt jwt) {
		return myEnrollmentQueryService.findMine(CurrentUser.from(jwt).employeeId(), status);
	}
}
