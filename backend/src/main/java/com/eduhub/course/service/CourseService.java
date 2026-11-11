package com.eduhub.course.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.eduhub.course.domain.Course;
import com.eduhub.course.domain.CourseSession;
import com.eduhub.course.dto.CourseDetailResponse;
import com.eduhub.course.dto.CourseSummaryResponse;
import com.eduhub.course.repository.CourseRepository;
import com.eduhub.course.repository.CourseSessionRepository;
import com.eduhub.course.repository.CourseSpecifications;
import com.eduhub.course.repository.SessionCount;
import com.eduhub.global.error.BusinessException;
import com.eduhub.global.error.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 과정 조회 (FR-01, FR-02).
 */
@Service
@Transactional(readOnly = true)
public class CourseService {

	private final CourseRepository courseRepository;
	private final CourseSessionRepository courseSessionRepository;

	public CourseService(CourseRepository courseRepository, CourseSessionRepository courseSessionRepository) {
		this.courseRepository = courseRepository;
		this.courseSessionRepository = courseSessionRepository;
	}

	/** FR-01: 제목·분류로 검색, 페이지 단위. 쿼리 3번(목록, 전체 개수, 회차 수 집계) */
	public Page<CourseSummaryResponse> getCourses(String keyword, String category, Pageable pageable) {
		Page<Course> courses = courseRepository.findAll(CourseSpecifications.search(keyword, category), pageable);
		List<Long> ids = courses.map(Course::getId).getContent();
		Map<Long, Long> counts = ids.isEmpty() ? Collections.emptyMap()
				: courseSessionRepository.countByCourseIds(ids).stream()
						.collect(Collectors.toMap(SessionCount::courseId, SessionCount::count));
		return courses.map(course -> CourseSummaryResponse.of(course, counts.getOrDefault(course.getId(), 0L)));
	}

	/** FR-02: 과정 상세와 회차(남은 자리 포함). 쿼리 2번(과정, 회차) */
	public CourseDetailResponse getCourse(Long id) {
		Course course = courseRepository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
		List<CourseSession> sessions = courseSessionRepository.findByCourseIdOrderBySessionNoAsc(id);
		return CourseDetailResponse.of(course, sessions);
	}
}
