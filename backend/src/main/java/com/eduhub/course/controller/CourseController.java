package com.eduhub.course.controller;

import com.eduhub.course.dto.CourseDetailResponse;
import com.eduhub.course.dto.CourseSummaryResponse;
import com.eduhub.course.service.CourseService;
import com.eduhub.global.common.PageResponse;
import com.eduhub.global.error.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 과정 조회 (FR-01, FR-02). 로그인 없이 호출할 수 있다 (D-22).
 */
@Tag(name = "Course", description = "교육과정 조회·관리")
@RestController
@RequestMapping("/api/v1/courses")
public class CourseController {

	private final CourseService courseService;

	public CourseController(CourseService courseService) {
		this.courseService = courseService;
	}

	@Operation(summary = "과정 목록 조회", description = "제목(keyword, 부분 일치)·분류(category, 정확히 일치)로 검색하고 페이지 단위로 조회한다 (page는 0부터)")
	@GetMapping
	public PageResponse<CourseSummaryResponse> getCourses(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String category,
			@ParameterObject @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.from(courseService.getCourses(keyword, category, pageable));
	}

	@Operation(summary = "과정 상세 조회", description = "회차별 일정·장소·정원·남은 자리를 함께 돌려준다")
	@ApiResponse(responseCode = "200", description = "조회 성공")
	@ApiResponse(responseCode = "404", description = "과정이 없음",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@GetMapping("/{courseId}")
	public CourseDetailResponse getCourse(@PathVariable Long courseId) {
		return courseService.getCourse(courseId);
	}
}
