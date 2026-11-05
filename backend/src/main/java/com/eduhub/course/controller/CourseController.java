package com.eduhub.course.controller;

import com.eduhub.course.dto.CourseResponse;
import com.eduhub.course.service.CourseService;
import com.eduhub.global.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "강의", description = "강의 조회·검색")
@RestController
@RequestMapping("/api/courses")
public class CourseController {

	private final CourseService courseService;

	public CourseController(CourseService courseService) {
		this.courseService = courseService;
	}

	@Operation(summary = "강의 목록 조회·검색", description = "keyword가 있으면 제목 또는 강사 이름으로 검색한다.")
	@GetMapping
	public PageResponse<CourseResponse> getCourses(
			@RequestParam(required = false) String keyword,
			@PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
		return PageResponse.from(courseService.getCourses(keyword, pageable));
	}

	@Operation(summary = "강의 단건 조회")
	@GetMapping("/{id}")
	public CourseResponse getCourse(@PathVariable Long id) {
		return courseService.getCourse(id);
	}
}
