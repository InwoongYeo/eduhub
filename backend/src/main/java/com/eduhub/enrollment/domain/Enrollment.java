package com.eduhub.enrollment.domain;

import java.time.Instant;

import com.eduhub.course.domain.CourseSession;
import com.eduhub.global.error.BusinessException;
import com.eduhub.global.error.ErrorCode;
import com.eduhub.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 임직원의 회차 수강 신청 (D-17). 임직원·회차 쌍당 한 행이라 같은 회차에 중복 신청할 수 없다 (FR-04).
 * 신청·대기 규칙(FR-04)은 apply·reapply로, 취소·승급(FR-06)은 cancel·promote로 구현했다.
 */
@Entity
@Table(name = "enrollment",
		uniqueConstraints = @UniqueConstraint(name = "uk_enrollment_employee_session", columnNames = {"employee_id", "session_id"}),
		indexes = @Index(name = "idx_enrollment_session_status", columnList = "session_id, status"))
public class Enrollment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "employee_id", nullable = false)
	private User employee;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id", nullable = false)
	private CourseSession session;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private EnrollmentStatus status;

	/** 대기 순번은 같은 회차의 WAITING 중 appliedAt 순서다 (FR-05). */
	@Column(nullable = false)
	private Instant appliedAt;

	protected Enrollment() {
	}

	private Enrollment(User employee, CourseSession session, EnrollmentStatus status) {
		this.employee = employee;
		this.session = session;
		this.status = status;
		this.appliedAt = Instant.now();
	}

	private Enrollment(User employee, CourseSession session, EnrollmentStatus status, Instant appliedAt) {
		this.employee = employee;
		this.session = session;
		this.status = status;
		this.appliedAt = appliedAt;
	}

	public static Enrollment create(User employee, CourseSession session, EnrollmentStatus status) {
		return new Enrollment(employee, session, status);
	}

	/**
	 * 새 수강 신청. 자리가 있으면 CONFIRMED, 없으면 WAITING으로 만든다 (FR-04).
	 */
	public static Enrollment apply(User employee, CourseSession session, boolean confirmed, Instant now) {
		return new Enrollment(employee, session, confirmed ? EnrollmentStatus.CONFIRMED : EnrollmentStatus.WAITING, now);
	}

	/**
	 * 취소된 신청을 같은 행에서 다시 신청한다 (D-02). CANCELLED가 아니면 중복 신청이다 (E001).
	 */
	public void reapply(boolean confirmed, Instant now) {
		if (status != EnrollmentStatus.CANCELLED) {
			throw new BusinessException(ErrorCode.DUPLICATE_ENROLLMENT);
		}
		this.status = confirmed ? EnrollmentStatus.CONFIRMED : EnrollmentStatus.WAITING;
		this.appliedAt = now;
	}

	/**
	 * 신청을 취소한다 (FR-06). CONFIRMED·WAITING만 CANCELLED로 바꾸고, 그 밖은 취소할 수 없다 (E005).
	 */
	public void cancel() {
		if (status != EnrollmentStatus.CONFIRMED && status != EnrollmentStatus.WAITING) {
			throw new BusinessException(ErrorCode.CANCEL_NOT_ALLOWED);
		}
		this.status = EnrollmentStatus.CANCELLED;
	}

	public boolean isConfirmed() {
		return status == EnrollmentStatus.CONFIRMED;
	}

	/**
	 * 대기자를 확정으로 승급한다 (FR-06). appliedAt은 바꾸지 않는다. WAITING이 아니면 서비스의 버그다.
	 */
	public void promote() {
		if (status != EnrollmentStatus.WAITING) {
			throw new IllegalStateException("대기 상태가 아닌 신청은 승급할 수 없습니다: " + status);
		}
		this.status = EnrollmentStatus.CONFIRMED;
	}

	public Long getId() {
		return id;
	}

	public User getEmployee() {
		return employee;
	}

	public CourseSession getSession() {
		return session;
	}

	public EnrollmentStatus getStatus() {
		return status;
	}

	public Instant getAppliedAt() {
		return appliedAt;
	}
}
