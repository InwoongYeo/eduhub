package com.eduhub.enrollment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import com.eduhub.course.domain.Course;
import com.eduhub.course.domain.CourseSession;
import com.eduhub.course.repository.CourseSessionRepository;
import com.eduhub.enrollment.domain.Enrollment;
import com.eduhub.enrollment.domain.EnrollmentStatus;
import com.eduhub.enrollment.dto.EnrollmentResponse;
import com.eduhub.enrollment.repository.EnrollmentRepository;
import com.eduhub.global.error.BusinessException;
import com.eduhub.global.error.ErrorCode;
import com.eduhub.user.domain.Role;
import com.eduhub.user.domain.User;
import com.eduhub.user.repository.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final Long SESSION_ID = 1L;
	private static final Long EMPLOYEE_ID = 10L;

	@Mock
	CourseSessionRepository courseSessionRepository;

	@Mock
	EnrollmentRepository enrollmentRepository;

	@Mock
	UserRepository userRepository;

	// 2026-12-01 09:00 서울
	private final LocalDateTime startsAt = LocalDateTime.of(2026, 12, 1, 9, 0);
	private Clock clock;
	private EnrollmentService enrollmentService;
	private Course course;
	private User employee;

	@BeforeEach
	void 준비() {
		clock = Clock.fixed(LocalDateTime.of(2026, 11, 16, 10, 0).atZone(SEOUL).toInstant(), SEOUL);
		enrollmentService = new EnrollmentService(courseSessionRepository, enrollmentRepository, userRepository, clock);
		course = Course.create("자바 기초", "입문", "개발");
		employee = User.create("emp@test.local", "x", "임직원", Role.EMPLOYEE);
	}

	private CourseSession session(int capacity) {
		CourseSession session = CourseSession.create(course, 1, startsAt, startsAt.plusHours(8), "본사", capacity);
		ReflectionTestUtils.setField(session, "id", SESSION_ID);
		return session;
	}

	private void givenLockedSession(CourseSession session) {
		given(courseSessionRepository.findByIdForUpdate(SESSION_ID)).willReturn(Optional.of(session));
	}

	private void givenActiveEmployee() {
		given(userRepository.findById(EMPLOYEE_ID)).willReturn(Optional.of(employee));
	}

	private void givenSaveReturnsArgument() {
		given(enrollmentRepository.saveAndFlush(any(Enrollment.class))).willAnswer(inv -> {
			Enrollment saved = inv.getArgument(0);
			if (saved.getId() == null) {
				ReflectionTestUtils.setField(saved, "id", 100L);
			}
			return saved;
		});
	}

	@Test
	void 자리가_있으면_CONFIRMED로_신청되고_확정_인원이_늘어난다() {
		CourseSession session = session(2);
		givenLockedSession(session);
		givenActiveEmployee();
		given(enrollmentRepository.findBySessionIdAndEmployeeId(SESSION_ID, EMPLOYEE_ID)).willReturn(Optional.empty());
		givenSaveReturnsArgument();

		EnrollmentResponse result = enrollmentService.enroll(SESSION_ID, EMPLOYEE_ID);

		assertThat(result.status()).isEqualTo(EnrollmentStatus.CONFIRMED);
		assertThat(result.enrollmentId()).isEqualTo(100L);
		assertThat(result.sessionId()).isEqualTo(SESSION_ID);
		assertThat(result.appliedAt()).isEqualTo(clock.instant());
		assertThat(session.getConfirmedCount()).isEqualTo(1);
		verify(enrollmentRepository, times(1)).saveAndFlush(any(Enrollment.class));
	}

	@Test
	void 정원이_차면_WAITING으로_신청되고_확정_인원은_그대로다() {
		CourseSession session = session(1);
		session.reserveSeat();
		givenLockedSession(session);
		givenActiveEmployee();
		given(enrollmentRepository.findBySessionIdAndEmployeeId(SESSION_ID, EMPLOYEE_ID)).willReturn(Optional.empty());
		givenSaveReturnsArgument();

		EnrollmentResponse result = enrollmentService.enroll(SESSION_ID, EMPLOYEE_ID);

		assertThat(result.status()).isEqualTo(EnrollmentStatus.WAITING);
		assertThat(session.getConfirmedCount()).isEqualTo(1);
	}

	@Test
	void U11_정원이_차면_앞선_대기자_수_더하기_1이_순번이다() {
		CourseSession session = session(1);
		session.reserveSeat();
		givenLockedSession(session);
		givenActiveEmployee();
		given(enrollmentRepository.findBySessionIdAndEmployeeId(SESSION_ID, EMPLOYEE_ID)).willReturn(Optional.empty());
		givenSaveReturnsArgument();
		given(enrollmentRepository.countWaitingAhead(SESSION_ID, clock.instant(), 100L)).willReturn(3L);

		EnrollmentResponse result = enrollmentService.enroll(SESSION_ID, EMPLOYEE_ID);

		assertThat(result.waitingNo()).isEqualTo(4);
	}

	@Test
	void U12_자리가_있으면_순번은_null이고_세지_않는다() {
		givenLockedSession(session(2));
		givenActiveEmployee();
		given(enrollmentRepository.findBySessionIdAndEmployeeId(SESSION_ID, EMPLOYEE_ID)).willReturn(Optional.empty());
		givenSaveReturnsArgument();

		EnrollmentResponse result = enrollmentService.enroll(SESSION_ID, EMPLOYEE_ID);

		assertThat(result.waitingNo()).isNull();
		verify(enrollmentRepository, never()).countWaitingAhead(any(), any(), any());
	}

	private Enrollment enrollmentWith(EnrollmentStatus status) {
		Enrollment e = Enrollment.create(employee, session(1), status);
		ReflectionTestUtils.setField(e, "id", 77L);
		return e;
	}

	@Test
	void U13a_내_신청이_대기이면_현재_순번을_돌려준다() {
		Enrollment waiting = enrollmentWith(EnrollmentStatus.WAITING);
		given(courseSessionRepository.existsById(SESSION_ID)).willReturn(true);
		given(enrollmentRepository.findBySessionIdAndEmployeeId(SESSION_ID, EMPLOYEE_ID))
				.willReturn(Optional.of(waiting));
		given(enrollmentRepository.countWaitingAhead(SESSION_ID, waiting.getAppliedAt(), 77L)).willReturn(2L);

		EnrollmentResponse result = enrollmentService.findMine(SESSION_ID, EMPLOYEE_ID);

		assertThat(result.status()).isEqualTo(EnrollmentStatus.WAITING);
		assertThat(result.waitingNo()).isEqualTo(3);
	}

	@Test
	void U13b_내_신청이_확정이면_순번은_null이다() {
		given(courseSessionRepository.existsById(SESSION_ID)).willReturn(true);
		given(enrollmentRepository.findBySessionIdAndEmployeeId(SESSION_ID, EMPLOYEE_ID))
				.willReturn(Optional.of(enrollmentWith(EnrollmentStatus.CONFIRMED)));

		EnrollmentResponse result = enrollmentService.findMine(SESSION_ID, EMPLOYEE_ID);

		assertThat(result.status()).isEqualTo(EnrollmentStatus.CONFIRMED);
		assertThat(result.waitingNo()).isNull();
		verify(enrollmentRepository, never()).countWaitingAhead(any(), any(), any());
	}

	@Test
	void U13c_신청한_적_없으면_E004이다() {
		given(courseSessionRepository.existsById(SESSION_ID)).willReturn(true);
		given(enrollmentRepository.findBySessionIdAndEmployeeId(SESSION_ID, EMPLOYEE_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> enrollmentService.findMine(SESSION_ID, EMPLOYEE_ID))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ENROLLMENT_NOT_FOUND));
	}

	@Test
	void U13d_회차가_없으면_C002이다() {
		given(courseSessionRepository.existsById(SESSION_ID)).willReturn(false);

		assertThatThrownBy(() -> enrollmentService.findMine(SESSION_ID, EMPLOYEE_ID))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SESSION_NOT_FOUND));
	}

	@Test
	void 회차가_없으면_C002이고_저장하지_않는다() {
		given(courseSessionRepository.findByIdForUpdate(SESSION_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> enrollmentService.enroll(SESSION_ID, EMPLOYEE_ID))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SESSION_NOT_FOUND));
		verify(enrollmentRepository, never()).saveAndFlush(any());
	}

	@Test
	void 종료된_회차에는_E002이다() {
		CourseSession session = session(2);
		session.close();
		givenLockedSession(session);

		assertThatThrownBy(() -> enrollmentService.enroll(SESSION_ID, EMPLOYEE_ID))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SESSION_NOT_OPEN));
		verify(enrollmentRepository, never()).saveAndFlush(any());
	}

	@Test
	void 시작_시각과_같으면_E003이다() {
		Clock atStart = Clock.fixed(startsAt.atZone(SEOUL).toInstant(), SEOUL);
		EnrollmentService service = new EnrollmentService(
				courseSessionRepository, enrollmentRepository, userRepository, atStart);
		givenLockedSession(session(2));

		assertThatThrownBy(() -> service.enroll(SESSION_ID, EMPLOYEE_ID))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ENROLLMENT_PERIOD_ENDED));
		verify(enrollmentRepository, never()).saveAndFlush(any());
	}

	@Test
	void 시작_시각이_지났으면_E003이다() {
		Clock afterStart = Clock.fixed(startsAt.plusMinutes(1).atZone(SEOUL).toInstant(), SEOUL);
		EnrollmentService service = new EnrollmentService(
				courseSessionRepository, enrollmentRepository, userRepository, afterStart);
		givenLockedSession(session(2));

		assertThatThrownBy(() -> service.enroll(SESSION_ID, EMPLOYEE_ID))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ENROLLMENT_PERIOD_ENDED));
		verify(enrollmentRepository, never()).saveAndFlush(any());
	}

	@Test
	void 이미_CONFIRMED_WAITING_COMPLETED이면_E001이고_확정_인원은_그대로다() {
		for (EnrollmentStatus status : new EnrollmentStatus[] {
				EnrollmentStatus.CONFIRMED, EnrollmentStatus.WAITING, EnrollmentStatus.COMPLETED}) {
			CourseSession session = session(2);
			givenLockedSession(session);
			givenActiveEmployee();
			given(enrollmentRepository.findBySessionIdAndEmployeeId(SESSION_ID, EMPLOYEE_ID))
					.willReturn(Optional.of(Enrollment.create(employee, session, status)));

			assertThatThrownBy(() -> enrollmentService.enroll(SESSION_ID, EMPLOYEE_ID))
					.isInstanceOfSatisfying(BusinessException.class,
							e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_ENROLLMENT));
			assertThat(session.getConfirmedCount()).as("상태 %s", status).isZero();
		}
		verify(enrollmentRepository, never()).saveAndFlush(any());
	}

	@Test
	void 취소된_신청은_자리가_있으면_같은_행이_CONFIRMED로_갱신된다() {
		CourseSession session = session(2);
		Enrollment cancelled = Enrollment.create(employee, session, EnrollmentStatus.CANCELLED);
		ReflectionTestUtils.setField(cancelled, "id", 55L);
		ReflectionTestUtils.setField(cancelled, "appliedAt", Instant.parse("2020-01-01T00:00:00Z"));
		givenLockedSession(session);
		givenActiveEmployee();
		given(enrollmentRepository.findBySessionIdAndEmployeeId(SESSION_ID, EMPLOYEE_ID))
				.willReturn(Optional.of(cancelled));
		givenSaveReturnsArgument();

		EnrollmentResponse result = enrollmentService.enroll(SESSION_ID, EMPLOYEE_ID);

		assertThat(result.enrollmentId()).isEqualTo(55L);
		assertThat(cancelled.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
		assertThat(cancelled.getAppliedAt()).isEqualTo(clock.instant());
		assertThat(session.getConfirmedCount()).isEqualTo(1);
	}

	@Test
	void 취소된_신청은_정원이_차면_같은_행이_WAITING으로_갱신된다() {
		CourseSession session = session(1);
		session.reserveSeat();
		Enrollment cancelled = Enrollment.create(employee, session, EnrollmentStatus.CANCELLED);
		ReflectionTestUtils.setField(cancelled, "id", 55L);
		givenLockedSession(session);
		givenActiveEmployee();
		given(enrollmentRepository.findBySessionIdAndEmployeeId(SESSION_ID, EMPLOYEE_ID))
				.willReturn(Optional.of(cancelled));
		givenSaveReturnsArgument();

		EnrollmentResponse result = enrollmentService.enroll(SESSION_ID, EMPLOYEE_ID);

		assertThat(result.enrollmentId()).isEqualTo(55L);
		assertThat(cancelled.getStatus()).isEqualTo(EnrollmentStatus.WAITING);
		assertThat(cancelled.getAppliedAt()).isEqualTo(clock.instant());
		assertThat(session.getConfirmedCount()).isEqualTo(1);
	}

	private Enrollment cancelTarget(CourseSession session, EnrollmentStatus status, Long id) {
		Enrollment e = Enrollment.create(employee, session, status);
		ReflectionTestUtils.setField(e, "id", id);
		return e;
	}

	private void givenMine(Enrollment mine) {
		given(enrollmentRepository.findBySessionIdAndEmployeeId(SESSION_ID, EMPLOYEE_ID)).willReturn(Optional.of(mine));
	}

	private void givenNextWaiting(Optional<Enrollment> next) {
		given(enrollmentRepository.findFirstBySessionIdAndStatusOrderByAppliedAtAscIdAsc(
				SESSION_ID, EnrollmentStatus.WAITING)).willReturn(next);
	}

	private void assertCancelError(EnrollmentService service, ErrorCode expected) {
		assertThatThrownBy(() -> service.cancel(SESSION_ID, EMPLOYEE_ID))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(expected));
		verify(enrollmentRepository, never()).saveAndFlush(any());
	}

	@Test
	void X1_확정_취소에_대기자가_있으면_1순위가_승급되고_확정_인원은_그대로다() {
		CourseSession session = session(1);
		session.reserveSeat();
		Enrollment mine = cancelTarget(session, EnrollmentStatus.CONFIRMED, 77L);
		Enrollment next = cancelTarget(session, EnrollmentStatus.WAITING, 78L);
		Instant nextAppliedAt = next.getAppliedAt();
		givenLockedSession(session);
		givenMine(mine);
		givenNextWaiting(Optional.of(next));
		givenSaveReturnsArgument();

		EnrollmentResponse result = enrollmentService.cancel(SESSION_ID, EMPLOYEE_ID);

		assertThat(result.status()).isEqualTo(EnrollmentStatus.CANCELLED);
		assertThat(result.waitingNo()).isNull();
		assertThat(result.enrollmentId()).isEqualTo(77L);
		assertThat(mine.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
		assertThat(next.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
		assertThat(next.getAppliedAt()).isEqualTo(nextAppliedAt);
		assertThat(session.getConfirmedCount()).isEqualTo(1);
	}

	@Test
	void X2_확정_취소에_대기자가_없으면_확정_인원이_1_줄어든다() {
		CourseSession session = session(2);
		session.reserveSeat();
		Enrollment mine = cancelTarget(session, EnrollmentStatus.CONFIRMED, 77L);
		givenLockedSession(session);
		givenMine(mine);
		givenNextWaiting(Optional.empty());
		givenSaveReturnsArgument();

		EnrollmentResponse result = enrollmentService.cancel(SESSION_ID, EMPLOYEE_ID);

		assertThat(result.status()).isEqualTo(EnrollmentStatus.CANCELLED);
		assertThat(session.getConfirmedCount()).isZero();
	}

	@Test
	void X3_대기_취소는_승급_조회를_하지_않고_확정_인원이_그대로다() {
		CourseSession session = session(1);
		session.reserveSeat();
		Enrollment mine = cancelTarget(session, EnrollmentStatus.WAITING, 77L);
		givenLockedSession(session);
		givenMine(mine);
		givenSaveReturnsArgument();

		EnrollmentResponse result = enrollmentService.cancel(SESSION_ID, EMPLOYEE_ID);

		assertThat(result.status()).isEqualTo(EnrollmentStatus.CANCELLED);
		assertThat(session.getConfirmedCount()).isEqualTo(1);
		verify(enrollmentRepository, never())
				.findFirstBySessionIdAndStatusOrderByAppliedAtAscIdAsc(any(), any());
	}

	@Test
	void X4a_취소할_회차가_없으면_C002이다() {
		given(courseSessionRepository.findByIdForUpdate(SESSION_ID)).willReturn(Optional.empty());

		assertCancelError(enrollmentService, ErrorCode.SESSION_NOT_FOUND);
	}

	@Test
	void X4b_신청이_없으면_E004이다() {
		givenLockedSession(session(1));
		given(enrollmentRepository.findBySessionIdAndEmployeeId(SESSION_ID, EMPLOYEE_ID)).willReturn(Optional.empty());

		assertCancelError(enrollmentService, ErrorCode.ENROLLMENT_NOT_FOUND);
	}

	@Test
	void X4c_시작_시각이_지났으면_E006이고_확정_인원은_그대로다() {
		Clock atStart = Clock.fixed(startsAt.atZone(SEOUL).toInstant(), SEOUL);
		EnrollmentService service = new EnrollmentService(
				courseSessionRepository, enrollmentRepository, userRepository, atStart);
		CourseSession session = session(1);
		session.reserveSeat();
		givenLockedSession(session);
		givenMine(cancelTarget(session, EnrollmentStatus.CONFIRMED, 77L));

		assertCancelError(service, ErrorCode.CANCEL_PERIOD_ENDED);
		assertThat(session.getConfirmedCount()).isEqualTo(1);
	}

	@Test
	void X4d_종료된_회차는_E006이다() {
		CourseSession session = session(1);
		session.close();
		givenLockedSession(session);
		givenMine(cancelTarget(session, EnrollmentStatus.WAITING, 77L));

		assertCancelError(enrollmentService, ErrorCode.CANCEL_PERIOD_ENDED);
	}

	@Test
	void X4e_이미_취소된_신청은_E005이고_확정_인원은_그대로다() {
		CourseSession session = session(1);
		session.reserveSeat();
		givenLockedSession(session);
		givenMine(cancelTarget(session, EnrollmentStatus.CANCELLED, 77L));

		assertCancelError(enrollmentService, ErrorCode.CANCEL_NOT_ALLOWED);
		assertThat(session.getConfirmedCount()).isEqualTo(1);
		verify(enrollmentRepository, never())
				.findFirstBySessionIdAndStatusOrderByAppliedAtAscIdAsc(any(), any());
	}

	private DataIntegrityViolationException integrityViolation(String constraintName) {
		SQLException sql = new SQLException("constraint violated");
		return new DataIntegrityViolationException("무결성 위반",
				new ConstraintViolationException("무결성 위반", sql, constraintName));
	}

	@Test
	void U9a_저장_중_유일_제약_위반이면_E001이다() {
		givenLockedSession(session(2));
		givenActiveEmployee();
		given(enrollmentRepository.findBySessionIdAndEmployeeId(SESSION_ID, EMPLOYEE_ID)).willReturn(Optional.empty());
		given(enrollmentRepository.saveAndFlush(any(Enrollment.class)))
				.willThrow(integrityViolation("uk_enrollment_employee_session"));

		assertThatThrownBy(() -> enrollmentService.enroll(SESSION_ID, EMPLOYEE_ID))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_ENROLLMENT));
	}

	@Test
	void U9b_다른_제약_위반이면_E001이_아니라_원래_예외가_전파된다() {
		givenLockedSession(session(2));
		givenActiveEmployee();
		given(enrollmentRepository.findBySessionIdAndEmployeeId(SESSION_ID, EMPLOYEE_ID)).willReturn(Optional.empty());
		DataIntegrityViolationException other = integrityViolation("ck_course_session_seats");
		given(enrollmentRepository.saveAndFlush(any(Enrollment.class))).willThrow(other);

		assertThatThrownBy(() -> enrollmentService.enroll(SESSION_ID, EMPLOYEE_ID)).isSameAs(other);
	}

	@Test
	void 사용자가_없으면_G002이다() {
		givenLockedSession(session(2));
		given(userRepository.findById(EMPLOYEE_ID)).willReturn(Optional.empty());

		assertThatThrownBy(() -> enrollmentService.enroll(SESSION_ID, EMPLOYEE_ID))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
		verify(enrollmentRepository, never()).saveAndFlush(any());
	}

	@Test
	void 비활성_사용자는_G002이다() {
		employee.deactivate();
		CourseSession session = session(2);
		givenLockedSession(session);
		givenActiveEmployee();

		assertThatThrownBy(() -> enrollmentService.enroll(SESSION_ID, EMPLOYEE_ID))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
		assertThat(session.getConfirmedCount()).isZero();
		verify(enrollmentRepository, never()).saveAndFlush(any());
	}
}
