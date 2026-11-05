package com.eduhub.course.repository;

import java.util.List;

import com.eduhub.course.domain.CourseSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseSessionRepository extends JpaRepository<CourseSession, Long> {

	/** 강의의 회차를 회차 번호 순으로 조회한다. */
	List<CourseSession> findByCourseIdOrderBySessionNoAsc(Long courseId);
}
