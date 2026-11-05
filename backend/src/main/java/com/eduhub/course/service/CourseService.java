package com.eduhub.course.service;

import com.eduhub.course.dto.CourseResponse;
import com.eduhub.course.repository.CourseRepository;
import com.eduhub.global.error.BusinessException;
import com.eduhub.global.error.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class CourseService {

	private final CourseRepository courseRepository;

	public CourseService(CourseRepository courseRepository) {
		this.courseRepository = courseRepository;
	}

	/** FR-03: 강의 목록 조회·검색 (페이지 단위, NFR-05) */
	public Page<CourseResponse> getCourses(String keyword, Pageable pageable) {
		if (!StringUtils.hasText(keyword)) {
			return courseRepository.findAll(pageable).map(CourseResponse::from);
		}
		String trimmed = keyword.trim();
		return courseRepository
				.findByTitleContainingOrInstructorNameContaining(trimmed, trimmed, pageable)
				.map(CourseResponse::from);
	}

	public CourseResponse getCourse(Long id) {
		return courseRepository.findById(id)
				.map(CourseResponse::from)
				.orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
	}
}
