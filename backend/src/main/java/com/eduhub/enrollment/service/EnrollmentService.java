package com.eduhub.enrollment.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;

import com.eduhub.course.domain.CourseSession;
import com.eduhub.course.repository.CourseSessionRepository;
import com.eduhub.enrollment.domain.Enrollment;
import com.eduhub.enrollment.domain.EnrollmentStatus;
import com.eduhub.enrollment.dto.EnrollmentResponse;
import com.eduhub.enrollment.repository.EnrollmentRepository;
import com.eduhub.global.error.BusinessException;
import com.eduhub.global.error.ErrorCode;
import com.eduhub.user.domain.User;
import com.eduhub.user.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회차 수강 신청 (FR-04). 같은 회차의 동시 신청은 회차 행 비관적 락으로 직렬화해 정원을 지킨다 (NFR-01).
 * 잠그는 행은 회차 하나뿐이라 교착 상태가 생기지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class EnrollmentService {

	private static final Logger log = LoggerFactory.getLogger(EnrollmentService.class);
	private static final String UNIQUE_CONSTRAINT = "uk_enrollment_employee_session";
	private static final int MAX_CAUSE_DEPTH = 10;

	private final CourseSessionRepository courseSessionRepository;
	private final EnrollmentRepository enrollmentRepository;
	private final UserRepository userRepository;
	private final Clock clock;

	public EnrollmentService(CourseSessionRepository courseSessionRepository,
			EnrollmentRepository enrollmentRepository, UserRepository userRepository, Clock clock) {
		this.courseSessionRepository = courseSessionRepository;
		this.enrollmentRepository = enrollmentRepository;
		this.userRepository = userRepository;
		this.clock = clock;
	}

	/**
	 * 신청한다. 자리가 있으면 CONFIRMED, 정원이 차면 WAITING으로 저장한다.
	 * 취소된 신청이 있으면 같은 행을 갱신한다 (D-02).
	 */
	@Transactional
	public EnrollmentResponse enroll(Long sessionId, Long employeeId) {
		CourseSession session = courseSessionRepository.findByIdForUpdate(sessionId)
				.orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
		session.checkEnrollable(LocalDateTime.now(clock));

		User employee = userRepository.findById(employeeId)
				.filter(User::isActive)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

		Enrollment existing = enrollmentRepository.findBySessionIdAndEmployeeId(sessionId, employeeId).orElse(null);
		// 중복 판정은 자리를 잡기 전에 한다. 거절된 요청이 확정 인원을 늘리면 안 된다.
		if (existing != null && existing.getStatus() != EnrollmentStatus.CANCELLED) {
			throw new BusinessException(ErrorCode.DUPLICATE_ENROLLMENT);
		}

		boolean confirmed = session.reserveSeat();
		Instant now = clock.instant();
		Enrollment enrollment;
		if (existing != null) {
			existing.reapply(confirmed, now);
			enrollment = existing;
		} else {
			enrollment = Enrollment.apply(employee, session, confirmed, now);
		}

		try {
			return EnrollmentResponse.from(enrollmentRepository.saveAndFlush(enrollment));
		} catch (DataIntegrityViolationException e) {
			if (isDuplicateEnrollment(e)) {
				throw new BusinessException(ErrorCode.DUPLICATE_ENROLLMENT);
			}
			// 다른 제약 위반(예: 정원 CHECK)은 중복 신청이 아니므로 E001로 가리지 않고 그대로 던진다 (→ 500).
			log.error("수강 신청 저장 중 예상하지 못한 무결성 위반: sessionId={}, employeeId={}, constraint={}",
					sessionId, employeeId, describeConstraint(e), e);
			throw e;
		}
	}

	/**
	 * 원인 사슬의 Hibernate 제약 위반이 (임직원, 회차) 유일 제약이면 true.
	 * MySQL은 제약 이름에 테이블 접두어를 붙일 수 있어 대소문자 무시 포함 여부로 본다.
	 * 동시성 테스트(다른 패키지)에서 호출하므로 public이다.
	 */
	public static boolean isDuplicateEnrollment(DataIntegrityViolationException e) {
		ConstraintViolationException cve = findConstraintViolation(e);
		if (cve == null || cve.getConstraintName() == null) {
			return false;
		}
		return cve.getConstraintName().toLowerCase(Locale.ROOT).contains(UNIQUE_CONSTRAINT);
	}

	private static ConstraintViolationException findConstraintViolation(Throwable e) {
		Throwable t = e;
		// 순환 참조 등으로 끝나지 않는 일이 없도록 깊이 상한을 둔다.
		for (int depth = 0; t != null && depth < MAX_CAUSE_DEPTH; depth++) {
			if (t instanceof ConstraintViolationException cve) {
				return cve;
			}
			t = t.getCause() == t ? null : t.getCause();
		}
		return null;
	}

	private static String describeConstraint(DataIntegrityViolationException e) {
		ConstraintViolationException cve = findConstraintViolation(e);
		return cve != null && cve.getConstraintName() != null ? cve.getConstraintName() : e.getMessage();
	}
}
