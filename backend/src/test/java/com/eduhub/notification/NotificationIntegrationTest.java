package com.eduhub.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.eduhub.course.dto.CourseRequest;
import com.eduhub.course.dto.SessionRequest;
import com.eduhub.course.dto.SessionResponse;
import com.eduhub.course.service.CourseAdminService;
import com.eduhub.enrollment.service.EnrollmentService;
import com.eduhub.notification.domain.NotificationType;
import com.eduhub.notification.repository.NotificationRepository;
import com.eduhub.user.domain.Role;
import com.eduhub.user.domain.User;
import com.eduhub.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림 기록 통합 테스트 (FR-09). 실제 커밋·롤백을 확인해야 하므로 클래스와 메서드에 @Transactional을 붙이지 않는다.
 */
@SpringBootTest
class NotificationIntegrationTest {

	private static final String COURSE_TITLE = "알림 통합 테스트 과정";

	@Autowired
	private EnrollmentService enrollmentService;
	@Autowired
	private CourseAdminService courseAdminService;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private NotificationRepository notificationRepository;
	@Autowired
	private TransactionTemplate transactionTemplate;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final List<Long> courseIds = new ArrayList<>();
	private final List<Long> userIds = new ArrayList<>();

	@AfterEach
	void tearDown() {
		// 이 테스트가 만든 행만 FK 순서(알림 -> 신청 -> 회차 -> 과정 -> 사용자)로 지운다
		jdbcTemplate.update("DELETE FROM notification WHERE employee_id IN (SELECT id FROM users WHERE email LIKE 'notify-%@test.local')");
		for (Long courseId : courseIds) {
			jdbcTemplate.update(
					"DELETE FROM enrollment WHERE session_id IN (SELECT id FROM course_session WHERE course_id = ?)", courseId);
			jdbcTemplate.update("DELETE FROM course_session WHERE course_id = ?", courseId);
			jdbcTemplate.update("DELETE FROM course WHERE id = ?", courseId);
		}
		jdbcTemplate.update("DELETE FROM enrollment WHERE employee_id IN (SELECT id FROM users WHERE email LIKE 'notify-%@test.local')");
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'notify-%@test.local'");
	}

	private Long newEmployee(int n) {
		User user = userRepository.save(User.create("notify-" + n + "@test.local", "x", "알림" + n, Role.EMPLOYEE));
		userIds.add(user.getId());
		return user.getId();
	}

	private SessionResponse newSession(int capacity) {
		Long courseId = courseAdminService.createCourse(new CourseRequest(COURSE_TITLE, "설명", "TEST")).id();
		courseIds.add(courseId);
		return courseAdminService.addSession(courseId, new SessionRequest(
				LocalDateTime.of(2099, 1, 1, 10, 0), LocalDateTime.of(2099, 1, 1, 18, 0), "대강당", capacity));
	}

	private int notificationCount(Long employeeId) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notification WHERE employee_id = ?", Integer.class,
				employeeId);
	}

	@Test
	@DisplayName("N1: 신청이 확정되면 ENROLLMENT_CONFIRMED 알림이 1건 저장되고 메시지에 과정 제목과 회차 번호가 있다")
	void 신청이_확정되면_알림이_1건_저장된다() {
		SessionResponse session = newSession(2);
		Long userId = newEmployee(1);

		enrollmentService.enroll(session.id(), userId);

		List<String> types = jdbcTemplate.queryForList("SELECT type FROM notification WHERE employee_id = ?", String.class,
				userId);
		List<String> messages = jdbcTemplate.queryForList("SELECT message FROM notification WHERE employee_id = ?",
				String.class, userId);
		assertThat(types).containsExactly(NotificationType.ENROLLMENT_CONFIRMED.name());
		assertThat(messages).hasSize(1);
		assertThat(messages.get(0)).contains(COURSE_TITLE).contains(session.sessionNo() + "회차");
		assertThat(notificationRepository.countByEmployeeIdAndType(userId, NotificationType.ENROLLMENT_CONFIRMED))
				.isEqualTo(1);
	}

	@Test
	@DisplayName("N2: 신청 트랜잭션이 롤백되면 신청, 알림, 확정 인원이 모두 남지 않는다 (알림이 같은 트랜잭션에 저장됨)")
	void 신청_트랜잭션이_롤백되면_알림도_남지_않는다() {
		SessionResponse session = newSession(2);
		Long userId = newEmployee(2);

		transactionTemplate.executeWithoutResult(status -> {
			enrollmentService.enroll(session.id(), userId);
			// 같은 트랜잭션 안에서는 알림이 보인다: "만들어진 뒤 함께 롤백됨"을 증명한다 (안 만들어서 0건인 경우와 구분)
			assertThat(notificationRepository.countByEmployeeIdAndType(userId, NotificationType.ENROLLMENT_CONFIRMED))
					.isEqualTo(1);
			status.setRollbackOnly();
		});

		Integer enrollments = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM enrollment WHERE session_id = ?",
				Integer.class, session.id());
		Integer confirmed = jdbcTemplate.queryForObject("SELECT confirmed_count FROM course_session WHERE id = ?",
				Integer.class, session.id());
		assertThat(enrollments).isZero();
		assertThat(notificationCount(userId)).isZero();
		assertThat(confirmed).isZero();
	}

	@Test
	@DisplayName("N3: 대기로 등록되면 알림이 저장되지 않는다")
	void 대기_등록은_알림이_없다() {
		SessionResponse session = newSession(1);
		Long first = newEmployee(3);
		Long second = newEmployee(4);

		enrollmentService.enroll(session.id(), first);
		enrollmentService.enroll(session.id(), second);

		assertThat(notificationCount(second)).isZero();
		assertThat(notificationCount(first)).isEqualTo(1);
	}

	@Test
	@DisplayName("N4: 확정자가 취소하면 승급자에게 WAITLIST_PROMOTED 1건이 저장되고 취소자는 확정 알림 1건만 있다")
	void 확정자_취소로_승급하면_승급_알림이_저장된다() {
		SessionResponse session = newSession(1);
		Long first = newEmployee(5);
		Long second = newEmployee(6);
		enrollmentService.enroll(session.id(), first);
		enrollmentService.enroll(session.id(), second);

		enrollmentService.cancel(session.id(), first);

		assertThat(notificationRepository.countByEmployeeIdAndType(second, NotificationType.WAITLIST_PROMOTED))
				.isEqualTo(1);
		assertThat(notificationCount(second)).isEqualTo(1);
		assertThat(notificationRepository.countByEmployeeIdAndType(first, NotificationType.ENROLLMENT_CONFIRMED))
				.isEqualTo(1);
		assertThat(notificationCount(first)).isEqualTo(1);
	}
}
