package com.eduhub.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.eduhub.course.domain.CourseSession;
import com.eduhub.notification.domain.Notification;
import com.eduhub.notification.domain.NotificationType;
import com.eduhub.notification.repository.NotificationRepository;
import com.eduhub.user.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신청 확정·승급 알림을 기록한다 (FR-09). 호출한 신청·취소 트랜잭션 안에서 바로 저장한다(B안).
 * 그래야 신청이 롤백되면 알림도 함께 사라지고, 확정됐는데 기록이 없는 경우가 생기지 않는다.
 * 로그에는 type·employeeId·sessionId만 남기고 이메일·이름 같은 개인정보는 넣지 않는다 (NFR-03).
 */
@Service
public class NotificationService {

	private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

	private final NotificationRepository notificationRepository;
	private final Clock clock;

	public NotificationService(NotificationRepository notificationRepository, Clock clock) {
		this.notificationRepository = notificationRepository;
		this.clock = clock;
	}

	/**
	 * 알림 한 건을 기록한다. 호출자의 트랜잭션이 없으면 실패한다(MANDATORY).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void record(User employee, CourseSession session, NotificationType type) {
		String message = buildMessage(session, type);
		// DB DATETIME(6)과 정밀도를 맞춘다
		Instant createdAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
		notificationRepository.save(Notification.create(employee, type, message, createdAt));
		log.info("notification type={} employeeId={} sessionId={}", type, employee.getId(), session.getId());
	}

	private String buildMessage(CourseSession session, NotificationType type) {
		String title = session.getCourse().getTitle();
		int sessionNo = session.getSessionNo();
		return switch (type) {
			case ENROLLMENT_CONFIRMED -> "'%s' %d회차 수강 신청이 확정되었습니다.".formatted(title, sessionNo);
			case WAITLIST_PROMOTED -> "'%s' %d회차 대기 중이던 신청이 확정되었습니다.".formatted(title, sessionNo);
		};
	}
}
