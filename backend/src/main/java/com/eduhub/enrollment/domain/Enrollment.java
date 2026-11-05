package com.eduhub.enrollment.domain;

import java.time.Instant;

import com.eduhub.course.domain.Course;
import com.eduhub.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 학생과 강의를 잇는 수강 기록. 학생·강의 쌍당 한 행이며, 재신청은 상태를 바꿔 처리한다 (D-02).
 */
@Entity
@Table(name = "enrollment",
		uniqueConstraints = @UniqueConstraint(name = "uk_enrollment_student_course", columnNames = {"student_id", "course_id"}))
public class Enrollment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "student_id", nullable = false)
	private User student;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "course_id", nullable = false)
	private Course course;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private EnrollmentStatus status;

	@Column(nullable = false)
	private Instant appliedAt;

	protected Enrollment() {
	}

	private Enrollment(User student, Course course) {
		this.student = student;
		this.course = course;
		this.status = EnrollmentStatus.APPLIED;
		this.appliedAt = Instant.now();
	}

	public static Enrollment apply(User student, Course course) {
		return new Enrollment(student, course);
	}

	public Long getId() {
		return id;
	}

	public User getStudent() {
		return student;
	}

	public Course getCourse() {
		return course;
	}

	public EnrollmentStatus getStatus() {
		return status;
	}

	public Instant getAppliedAt() {
		return appliedAt;
	}
}
