package com.eduhub.enrollment.repository;

import java.util.Optional;

import com.eduhub.enrollment.domain.Enrollment;
import com.eduhub.enrollment.domain.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

	/** (회차, 임직원) 쌍의 신청 이력을 조회한다. 쌍은 유일하다. */
	Optional<Enrollment> findBySessionIdAndEmployeeId(Long sessionId, Long employeeId);

	/** 회차의 특정 상태 신청 수. */
	long countBySessionIdAndStatus(Long sessionId, EnrollmentStatus status);
}
