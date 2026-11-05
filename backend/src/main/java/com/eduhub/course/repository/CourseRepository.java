package com.eduhub.course.repository;

import com.eduhub.course.domain.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {

	/** 목록 조회 시 강사를 함께 가져와 N+1 조회를 막는다 (NFR-05). */
	@Override
	@EntityGraph(attributePaths = "instructor")
	Page<Course> findAll(Pageable pageable);

	/** D-05: 제목 또는 강사 이름으로 검색 */
	@EntityGraph(attributePaths = "instructor")
	Page<Course> findByTitleContainingOrInstructorNameContaining(String title, String instructorName, Pageable pageable);
}
