package com.eduhub.course.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.eduhub.course.domain.CourseSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseSessionRepository extends JpaRepository<CourseSession, Long> {

	/**
	 * 회차 행을 쓰기 잠금(SELECT ... FOR UPDATE)으로 조회한다 (NFR-01).
	 * 같은 회차에 대한 신청을 직렬화해 정원 초과와 대기 순번 중복을 막는다. 트랜잭션 안에서 호출해야 한다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from CourseSession s where s.id = :id")
	Optional<CourseSession> findByIdForUpdate(@Param("id") Long id);

	/**
	 * 과정의 회차를 모두 쓰기 잠금으로 조회한다. InnoDB는 uk_course_session_no 인덱스 순서로 행과 마지막 행 뒤 간격까지 잠근다
	 * (order by는 결과 정렬일 뿐). 같은 과정의 addSession과 동시에 실행되면 교착이 생길 수 있다 (D-32).
	 * 트랜잭션 안에서 호출해야 한다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from CourseSession s where s.course.id = :courseId order by s.id")
	List<CourseSession> findByCourseIdForUpdate(@Param("courseId") Long courseId);

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
