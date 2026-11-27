package com.eduhub.enrollment.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.eduhub.enrollment.domain.Enrollment;
import com.eduhub.enrollment.domain.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

	/** (회차, 임직원) 쌍의 신청 이력을 조회한다. 쌍은 유일하다. */
	Optional<Enrollment> findBySessionIdAndEmployeeId(Long sessionId, Long employeeId);

	/** 회차의 특정 상태 신청 수. */
	long countBySessionIdAndStatus(Long sessionId, EnrollmentStatus status);

	/** 승급 대상: 같은 회차 대기자 중 appliedAt, id 오름차순 첫 행. 대기 순번(D-36)과 같은 정렬이다 (FR-06). */
	Optional<Enrollment> findFirstBySessionIdAndStatusOrderByAppliedAtAscIdAsc(Long sessionId, EnrollmentStatus status);

	/** 같은 회차에서 나보다 앞선 대기자 수 (appliedAt, id 순). waitingNo = 이 값 + 1 (FR-05) */
	@Query("""
			select count(e) from Enrollment e
			where e.session.id = :sessionId
			  and e.status = com.eduhub.enrollment.domain.EnrollmentStatus.WAITING
			  and (e.appliedAt < :appliedAt or (e.appliedAt = :appliedAt and e.id < :id))""")
	long countWaitingAhead(@Param("sessionId") Long sessionId, @Param("appliedAt") Instant appliedAt,
			@Param("id") Long id);

	/**
	 * 임직원의 신청 내역 (FR-07). SQL 1개로 과정·회차를 조인하고 대기 순번을 상관 서브쿼리로 계산한다.
	 * 정렬은 신청 시각 내림차순, 같으면 id 내림차순. {@code status}가 null이면 전체.
	 * 대기 순번 비교식은 {@link #countWaitingAhead}와 같아야 한다(D-36). 한쪽을 바꾸면 다른 쪽도 함께 바꾼다.
	 */
	@Query("""
			select new com.eduhub.enrollment.repository.MyEnrollmentRow(
			         e.id, s.id, c.title, s.startsAt, s.location, e.status, e.appliedAt,
			         case when e.status = com.eduhub.enrollment.domain.EnrollmentStatus.WAITING then
			           (select count(w) + 1 from Enrollment w
			             where w.session = s
			               and w.status = com.eduhub.enrollment.domain.EnrollmentStatus.WAITING
			               and (w.appliedAt < e.appliedAt or (w.appliedAt = e.appliedAt and w.id < e.id)))
			         end)
			from Enrollment e join e.session s join s.course c
			where e.employee.id = :employeeId
			  and (:status is null or e.status = :status)
			order by e.appliedAt desc, e.id desc""")
	List<MyEnrollmentRow> findMyEnrollments(@Param("employeeId") Long employeeId,
			@Param("status") EnrollmentStatus status);
}
