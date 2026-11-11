package com.eduhub.course.repository;

import java.util.Collection;
import java.util.List;

import com.eduhub.course.domain.CourseSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseSessionRepository extends JpaRepository<CourseSession, Long> {

	/** 과정의 회차를 회차 번호 순으로 조회한다. */
	List<CourseSession> findByCourseIdOrderBySessionNoAsc(Long courseId);

	/** 다음 회차 번호를 정하기 위한 현재 최대 번호. 회차가 없으면 0. */
	@Query("select coalesce(max(s.sessionNo), 0) from CourseSession s where s.course.id = :courseId")
	int findMaxSessionNo(@Param("courseId") Long courseId);

	/** 목록의 회차 수를 과정마다 따로 세지 않고 한 번에 집계한다 (NFR-02, N+1 방지). */
	@Query("select new com.eduhub.course.repository.SessionCount(s.course.id, count(s)) "
			+ "from CourseSession s where s.course.id in :courseIds group by s.course.id")
	List<SessionCount> countByCourseIds(@Param("courseIds") Collection<Long> courseIds);
}
