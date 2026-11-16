package com.eduhub.course.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import com.eduhub.global.error.BusinessException;
import com.eduhub.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

class CourseSessionTest {

	private static final LocalDateTime STARTS_AT = LocalDateTime.of(2099, 1, 10, 9, 0);

	private CourseSession session(int capacity) {
		Course course = Course.create("스프링 입문", "설명", "개발");
		return CourseSession.create(course, 1, STARTS_AT, STARTS_AT.plusHours(8), "본사", capacity);
	}

	private void assertErrorCode(Runnable action, ErrorCode expected) {
		assertThatThrownBy(action::run)
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(expected));
	}

	@Test
	void 자리가_있는_동안만_확정_인원이_늘어난다() {
		CourseSession session = session(2);

		assertThat(session.reserveSeat()).isTrue();
		assertThat(session.reserveSeat()).isTrue();
		assertThat(session.reserveSeat()).isFalse();
		assertThat(session.getConfirmedCount()).isEqualTo(2);
	}

	@Test
	void 시작_1분_전까지는_신청할_수_있다() {
		assertThatCode(() -> session(1).checkEnrollable(STARTS_AT.minusMinutes(1))).doesNotThrowAnyException();
	}

	@Test
	void 시작_시각과_같으면_신청_기간_종료_E003이다() {
		assertErrorCode(() -> session(1).checkEnrollable(STARTS_AT), ErrorCode.ENROLLMENT_PERIOD_ENDED);
	}

	@Test
	void 시작_이후이면_신청_기간_종료_E003이다() {
		assertErrorCode(() -> session(1).checkEnrollable(STARTS_AT.plusMinutes(1)), ErrorCode.ENROLLMENT_PERIOD_ENDED);
	}

	@Test
	void 종료된_회차는_E002이다() {
		CourseSession session = session(1);
		session.close();

		assertErrorCode(() -> session.checkEnrollable(STARTS_AT.minusMinutes(1)), ErrorCode.SESSION_NOT_OPEN);
	}

	@Test
	void 시작_1분_전까지는_취소할_수_있다() {
		assertThatCode(() -> session(1).checkCancellable(STARTS_AT.minusMinutes(1))).doesNotThrowAnyException();
	}

	@Test
	void 시작_시각과_같으면_취소_기간_종료_E006이다() {
		assertErrorCode(() -> session(1).checkCancellable(STARTS_AT), ErrorCode.CANCEL_PERIOD_ENDED);
	}

	@Test
	void 시작_이후이면_취소_기간_종료_E006이다() {
		assertErrorCode(() -> session(1).checkCancellable(STARTS_AT.plusMinutes(1)), ErrorCode.CANCEL_PERIOD_ENDED);
	}

	@Test
	void 종료된_회차는_시작_전이어도_취소_기간_종료_E006이다() {
		CourseSession session = session(1);
		session.close();

		assertErrorCode(() -> session.checkCancellable(STARTS_AT.minusMinutes(1)), ErrorCode.CANCEL_PERIOD_ENDED);
	}

	@Test
	void 자리를_반환하면_확정_인원이_1_줄고_0이면_예외이다() {
		CourseSession session = session(2);
		session.reserveSeat();

		session.releaseSeat();

		assertThat(session.getConfirmedCount()).isZero();
		assertThatThrownBy(session::releaseSeat).isInstanceOf(IllegalStateException.class);
		assertThat(session.getConfirmedCount()).isZero();
	}

	@Test
	void 종료되고_시작도_지났으면_E002가_먼저다() {
		CourseSession session = session(1);
		session.close();

		assertErrorCode(() -> session.checkEnrollable(STARTS_AT.plusDays(1)), ErrorCode.SESSION_NOT_OPEN);
	}
}
