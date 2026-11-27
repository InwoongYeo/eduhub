package com.eduhub.enrollment.service;

import java.util.List;

import com.eduhub.enrollment.domain.EnrollmentStatus;
import com.eduhub.enrollment.dto.MyEnrollmentResponse;
import com.eduhub.enrollment.repository.EnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 신청 내역 조회 전용 (FR-07). 신청·취소는 {@link EnrollmentService}가 맡는다.
 * 리포지토리를 한 번만 호출한다 (SQL 1개, NFR-02).
 */
@Service
@Transactional(readOnly = true)
public class MyEnrollmentQueryService {

	private final EnrollmentRepository enrollmentRepository;

	public MyEnrollmentQueryService(EnrollmentRepository enrollmentRepository) {
		this.enrollmentRepository = enrollmentRepository;
	}

	public List<MyEnrollmentResponse> findMine(Long employeeId, EnrollmentStatus status) {
		return enrollmentRepository.findMyEnrollments(employeeId, status).stream()
				.map(MyEnrollmentResponse::from)
				.toList();
	}
}
