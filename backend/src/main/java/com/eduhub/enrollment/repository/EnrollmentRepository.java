package com.eduhub.enrollment.repository;

import java.time.Instant;
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

	/** 같은 회차에서 나보다 앞선 대기자 수 (appliedAt, id 순). waitingNo = 이 값 + 1 (FR-05) */
	@Query("""
			select count(e) from Enrollment e
			where e.session.id = :sessionId
			  and e.status = com.eduhub.enrollment.domain.EnrollmentStatus.WAITING
			  and (e.appliedAt < :appliedAt or (e.appliedAt = :appliedAt and e.id < :id))""")
	long countWaitingAhead(@Param("sessionId") Long sessionId, @Param("appliedAt") Instant appliedAt,
			@Param("id") Long id);
}
