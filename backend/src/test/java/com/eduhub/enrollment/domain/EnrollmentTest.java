package com.eduhub.enrollment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDateTime;

import com.eduhub.course.domain.Course;
import com.eduhub.course.domain.CourseSession;
import com.eduhub.global.error.BusinessException;
import com.eduhub.global.error.ErrorCode;
import com.eduhub.user.domain.Role;
import com.eduhub.user.domain.User;
import org.junit.jupiter.api.Test;

class EnrollmentTest {

	private static final Instant NOW = Instant.parse("2026-11-16T01:00:00Z");

	private final User employee = User.create("emp@test.local", "x", "임직원", Role.EMPLOYEE);
	private final CourseSession session = CourseSession.create(Course.create("스프링 입문", "설명", "개발"), 1,
			LocalDateTime.of(2099, 1, 10, 9, 0), LocalDateTime.of(2099, 1, 10, 18, 0), "본사", 10);

	private Enrollment enrollmentWith(EnrollmentStatus status) {
		return Enrollment.create(employee, session, status);
	}

	@Test
	void 자리가_있으면_확정으로_신청된다() {
		Enrollment enrollment = Enrollment.apply(employee, session, true, NOW);

		assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
		assertThat(enrollment.getAppliedAt()).isEqualTo(NOW);
	}

	@Test
	void 정원이_차면_대기로_신청된다() {
		Enrollment enrollment = Enrollment.apply(employee, session, false, NOW);

		assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.WAITING);
		assertThat(enrollment.getAppliedAt()).isEqualTo(NOW);
	}

	@Test
	void 취소된_신청은_자리가_있으면_확정으로_재신청된다() {
		Enrollment enrollment = enrollmentWith(EnrollmentStatus.CANCELLED);

		enrollment.reapply(true, NOW);

		assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
		assertThat(enrollment.getAppliedAt()).isEqualTo(NOW);
	}

	@Test
	void 취소된_신청은_정원이_차면_대기로_재신청된다() {
		Enrollment enrollment = enrollmentWith(EnrollmentStatus.CANCELLED);

		enrollment.reapply(false, NOW);

		assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.WAITING);
		assertThat(enrollment.getAppliedAt()).isEqualTo(NOW);
	}

	@Test
	void 확정과_대기_신청은_취소할_수_있다() {
		for (EnrollmentStatus status : new EnrollmentStatus[] {EnrollmentStatus.CONFIRMED, EnrollmentStatus.WAITING}) {
			Enrollment enrollment = enrollmentWith(status);

			enrollment.cancel();

			assertThat(enrollment.getStatus()).as("상태 %s", status).isEqualTo(EnrollmentStatus.CANCELLED);
		}
	}

	@Test
	void 취소_수료_상태에서_취소하면_E005이다() {
		for (EnrollmentStatus status : new EnrollmentStatus[] {EnrollmentStatus.CANCELLED, EnrollmentStatus.COMPLETED}) {
			Enrollment enrollment = enrollmentWith(status);

			assertThatThrownBy(enrollment::cancel)
					.isInstanceOfSatisfying(BusinessException.class,
							e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CANCEL_NOT_ALLOWED));
			assertThat(enrollment.getStatus()).isEqualTo(status);
		}
	}

	@Test
	void 대기_신청은_승급하면_확정이_되고_신청_시각은_그대로다() {
		Enrollment enrollment = Enrollment.apply(employee, session, false, NOW);

		enrollment.promote();

		assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
		assertThat(enrollment.getAppliedAt()).isEqualTo(NOW);
	}

	@Test
	void 대기가_아닌_신청을_승급하면_IllegalStateException이다() {
		for (EnrollmentStatus status : new EnrollmentStatus[] {
				EnrollmentStatus.CONFIRMED, EnrollmentStatus.CANCELLED, EnrollmentStatus.COMPLETED}) {
			Enrollment enrollment = enrollmentWith(status);

			assertThatThrownBy(enrollment::promote).isInstanceOf(IllegalStateException.class);
			assertThat(enrollment.getStatus()).isEqualTo(status);
		}
	}

	@Test
	void 확정_여부는_CONFIRMED_상태만_true이다() {
		assertThat(enrollmentWith(EnrollmentStatus.CONFIRMED).isConfirmed()).isTrue();
		assertThat(enrollmentWith(EnrollmentStatus.WAITING).isConfirmed()).isFalse();
		assertThat(enrollmentWith(EnrollmentStatus.CANCELLED).isConfirmed()).isFalse();
		assertThat(enrollmentWith(EnrollmentStatus.COMPLETED).isConfirmed()).isFalse();
	}

	@Test
	void 확정_대기_수료_상태에서_재신청하면_E001이다() {
		for (EnrollmentStatus status : new EnrollmentStatus[] {
				EnrollmentStatus.CONFIRMED, EnrollmentStatus.WAITING, EnrollmentStatus.COMPLETED}) {
			Enrollment enrollment = enrollmentWith(status);

			assertThatThrownBy(() -> enrollment.reapply(true, NOW))
					.isInstanceOfSatisfying(BusinessException.class,
							e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_ENROLLMENT));
			assertThat(enrollment.getStatus()).isEqualTo(status);
		}
	}
}
