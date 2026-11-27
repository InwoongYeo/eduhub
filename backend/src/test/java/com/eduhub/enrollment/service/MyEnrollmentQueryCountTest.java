package com.eduhub.enrollment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.eduhub.course.domain.Course;
import com.eduhub.course.domain.CourseSession;
import com.eduhub.course.repository.CourseRepository;
import com.eduhub.course.repository.CourseSessionRepository;
import com.eduhub.enrollment.domain.Enrollment;
import com.eduhub.enrollment.domain.EnrollmentStatus;
import com.eduhub.enrollment.dto.MyEnrollmentResponse;
import com.eduhub.enrollment.repository.EnrollmentRepository;
import com.eduhub.user.domain.Role;
import com.eduhub.user.domain.User;
import com.eduhub.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 신청 내역 조회는 행 수와 관계없이 SQL 1개여야 한다 (NFR-02, N+1 회귀 방지).
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class MyEnrollmentQueryCountTest {

	@Autowired
	MyEnrollmentQueryService service;

	@Autowired
	UserRepository userRepository;

	@Autowired
	CourseRepository courseRepository;

	@Autowired
	CourseSessionRepository courseSessionRepository;

	@Autowired
	EnrollmentRepository enrollmentRepository;

	@Autowired
	EntityManager entityManager;

	private final LocalDateTime base = LocalDateTime.of(2099, 1, 1, 9, 0);

	private User user() {
		return userRepository.save(
				User.create("count-" + UUID.randomUUID() + "@test.local", "encoded", "임직원", Role.EMPLOYEE));
	}

	/** 서로 다른 과정·회차마다 신청 1건씩, 상태는 확정·대기·취소를 돌아가며 준비한다. */
	private void prepare(User me, int count) {
		EnrollmentStatus[] statuses = {
				EnrollmentStatus.CONFIRMED, EnrollmentStatus.WAITING, EnrollmentStatus.CANCELLED };
		for (int i = 0; i < count; i++) {
			Course course = courseRepository.save(Course.create("카운트 과정 " + i, "설명", "개발"));
			CourseSession session = courseSessionRepository.save(CourseSession.create(
					course, 1, base.plusDays(i), base.plusDays(i).plusHours(8), "장소 " + i, 30));
			enrollmentRepository.save(Enrollment.create(me, session, statuses[i % 3]));
			// 대기 순번 서브쿼리가 의미를 갖도록 같은 회차에 다른 대기자를 하나 둔다
			if (statuses[i % 3] == EnrollmentStatus.WAITING) {
				enrollmentRepository.save(Enrollment.create(user(), session, EnrollmentStatus.WAITING));
			}
		}
	}

	private record Measured(long prepareStatementCount, List<MyEnrollmentResponse> result) {
	}

	private Measured measure(User me) {
		entityManager.flush();
		entityManager.clear();
		Statistics statistics = entityManager.getEntityManagerFactory()
				.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();

		List<MyEnrollmentResponse> result = service.findMine(me.getId(), null);

		return new Measured(statistics.getPrepareStatementCount(), result);
	}

	/** 대기 행은 같은 회차에서 내가 먼저 신청했으므로 순번 1, 그 밖의 상태는 null이어야 한다. */
	private void assertWaitingNoFilled(List<MyEnrollmentResponse> result, long expectedWaitingRows) {
		assertThat(result).filteredOn(r -> r.status() == EnrollmentStatus.WAITING)
				.hasSize((int) expectedWaitingRows)
				.allSatisfy(r -> assertThat(r.waitingNo()).isEqualTo(1));
		assertThat(result).filteredOn(r -> r.status() != EnrollmentStatus.WAITING)
				.allSatisfy(r -> assertThat(r.waitingNo()).isNull());
	}

	@Test
	void 신청_3건을_조회해도_SQL은_1개이고_대기_순번이_채워진다() {
		User me = user();
		prepare(me, 3);

		Measured measured = measure(me);

		assertThat(measured.prepareStatementCount()).isEqualTo(1);
		assertThat(measured.result()).hasSize(3);
		assertWaitingNoFilled(measured.result(), 1);
	}

	@Test
	void 신청_6건으로_늘어도_SQL은_1개이고_대기_순번이_채워진다() {
		User me = user();
		prepare(me, 6);

		Measured measured = measure(me);

		assertThat(measured.prepareStatementCount()).isEqualTo(1);
		assertThat(measured.result()).hasSize(6);
		assertWaitingNoFilled(measured.result(), 2);
	}
}
