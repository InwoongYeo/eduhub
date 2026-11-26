package com.eduhub.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.eduhub.course.domain.Course;
import com.eduhub.course.domain.CourseSession;
import com.eduhub.notification.domain.Notification;
import com.eduhub.notification.domain.NotificationType;
import com.eduhub.notification.repository.NotificationRepository;
import com.eduhub.user.domain.Role;
import com.eduhub.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	@Mock
	private NotificationRepository notificationRepository;

	private Instant now;
	private NotificationService service;
	private User employee;
	private CourseSession session;

	@BeforeEach
	void setUp() {
		// 마이크로초보다 정밀한 시각을 줘서 자르는지 확인한다
		now = LocalDateTime.of(2026, 11, 16, 10, 0).atZone(SEOUL).toInstant().plusNanos(123_456_789);
		service = new NotificationService(notificationRepository, Clock.fixed(now, SEOUL));
		employee = User.create("emp@test.local", "x", "임직원", Role.EMPLOYEE);
		Course course = Course.create("자바 기초", "입문", "개발");
		LocalDateTime startsAt = LocalDateTime.of(2026, 12, 1, 9, 0);
		session = CourseSession.create(course, 2, startsAt, startsAt.plusHours(8), "본사", 10);
	}

	private Notification recorded(NotificationType type) {
		service.record(employee, session, type);
		ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
		verify(notificationRepository, times(1)).save(captor.capture());
		return captor.getValue();
	}

	@Test
	void 신청_확정_알림은_고정_문구로_저장한다() {
		Notification saved = recorded(NotificationType.ENROLLMENT_CONFIRMED);

		assertThat(saved.getMessage()).isEqualTo("'자바 기초' 2회차 수강 신청이 확정되었습니다.");
		assertThat(saved.getType()).isEqualTo(NotificationType.ENROLLMENT_CONFIRMED);
		assertThat(saved.getEmployee()).isSameAs(employee);
	}

	@Test
	void 대기_승급_알림은_고정_문구로_저장한다() {
		Notification saved = recorded(NotificationType.WAITLIST_PROMOTED);

		assertThat(saved.getMessage()).isEqualTo("'자바 기초' 2회차 대기 중이던 신청이 확정되었습니다.");
		assertThat(saved.getType()).isEqualTo(NotificationType.WAITLIST_PROMOTED);
		assertThat(saved.getEmployee()).isSameAs(employee);
	}

	@Test
	void 생성_시각은_Clock_시각을_마이크로초로_자른_값이다() {
		Notification saved = recorded(NotificationType.ENROLLMENT_CONFIRMED);

		assertThat(saved.getCreatedAt()).isEqualTo(now.minusNanos(789));
	}
}
