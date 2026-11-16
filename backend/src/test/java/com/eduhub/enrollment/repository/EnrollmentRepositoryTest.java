package com.eduhub.enrollment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import com.eduhub.course.domain.Course;
import com.eduhub.course.domain.CourseSession;
import com.eduhub.course.repository.CourseRepository;
import com.eduhub.course.repository.CourseSessionRepository;
import com.eduhub.enrollment.domain.Enrollment;
import com.eduhub.enrollment.domain.EnrollmentStatus;
import com.eduhub.user.domain.Role;
import com.eduhub.user.domain.User;
import com.eduhub.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.util.ReflectionTestUtils;

@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:jpatest-enrollment;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EnrollmentRepositoryTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	CourseRepository courseRepository;

	@Autowired
	CourseSessionRepository courseSessionRepository;

	@Autowired
	EnrollmentRepository enrollmentRepository;

	@Autowired
	EntityManager em;

	private final LocalDateTime base = LocalDateTime.of(2026, 12, 1, 9, 0);

	private User user(String email) {
		return userRepository.save(User.create(email, "encoded", "임직원", Role.EMPLOYEE));
	}

	private CourseSession session(Course course, int no) {
		return courseSessionRepository.save(
				CourseSession.create(course, no, base.plusWeeks(no), base.plusWeeks(no).plusHours(8), "본사", 30));
	}

	@Test
	void 회차와_임직원_쌍으로_신청을_조회하고_다른_쌍은_비어_있다() {
		Course course = courseRepository.save(Course.create("자바 기초", "입문", "개발"));
		CourseSession s1 = session(course, 1);
		CourseSession s2 = session(course, 2);
		User a = user("a@eduhub.local");
		User b = user("b@eduhub.local");
		Enrollment saved = enrollmentRepository.save(Enrollment.create(a, s1, EnrollmentStatus.CONFIRMED));
		em.flush();
		em.clear();

		assertThat(enrollmentRepository.findBySessionIdAndEmployeeId(s1.getId(), a.getId()))
				.get().extracting(Enrollment::getId).isEqualTo(saved.getId());
		assertThat(enrollmentRepository.findBySessionIdAndEmployeeId(s2.getId(), a.getId())).isEmpty();
		assertThat(enrollmentRepository.findBySessionIdAndEmployeeId(s1.getId(), b.getId())).isEmpty();
	}

	@Test
	void 회차의_상태별_신청_수를_센다() {
		Course course = courseRepository.save(Course.create("자바 기초", "입문", "개발"));
		CourseSession s1 = session(course, 1);
		enrollmentRepository.save(Enrollment.create(user("a@eduhub.local"), s1, EnrollmentStatus.CONFIRMED));
		enrollmentRepository.save(Enrollment.create(user("b@eduhub.local"), s1, EnrollmentStatus.CONFIRMED));
		enrollmentRepository.save(Enrollment.create(user("c@eduhub.local"), s1, EnrollmentStatus.WAITING));
		em.flush();
		em.clear();

		assertThat(enrollmentRepository.countBySessionIdAndStatus(s1.getId(), EnrollmentStatus.CONFIRMED)).isEqualTo(2);
		assertThat(enrollmentRepository.countBySessionIdAndStatus(s1.getId(), EnrollmentStatus.WAITING)).isEqualTo(1);
	}

	private Enrollment enrollment(User user, CourseSession session, EnrollmentStatus status, Instant appliedAt) {
		Enrollment e = Enrollment.create(user, session, status);
		ReflectionTestUtils.setField(e, "appliedAt", appliedAt);
		return enrollmentRepository.save(e);
	}

	private long ahead(Enrollment e) {
		return enrollmentRepository.countWaitingAhead(e.getSession().getId(), e.getAppliedAt(), e.getId());
	}

	@Test
	void 대기자_앞선_수는_신청_시각_순서로_0_1_2이다() {
		Course course = courseRepository.save(Course.create("자바 기초", "입문", "개발"));
		CourseSession s1 = session(course, 1);
		Instant t = Instant.parse("2026-11-16T01:00:00Z");
		Enrollment w1 = enrollment(user("a@eduhub.local"), s1, EnrollmentStatus.WAITING, t);
		Enrollment w2 = enrollment(user("b@eduhub.local"), s1, EnrollmentStatus.WAITING, t.plusSeconds(1));
		Enrollment w3 = enrollment(user("c@eduhub.local"), s1, EnrollmentStatus.WAITING, t.plusSeconds(2));
		em.flush();
		em.clear();

		assertThat(ahead(w1)).isZero();
		assertThat(ahead(w2)).isEqualTo(1);
		assertThat(ahead(w3)).isEqualTo(2);
	}

	@Test
	void 같은_시각이면_id가_작은_쪽이_앞선다() {
		Course course = courseRepository.save(Course.create("자바 기초", "입문", "개발"));
		CourseSession s1 = session(course, 1);
		Instant t = Instant.parse("2026-11-16T01:00:00Z");
		Enrollment first = enrollment(user("a@eduhub.local"), s1, EnrollmentStatus.WAITING, t);
		Enrollment second = enrollment(user("b@eduhub.local"), s1, EnrollmentStatus.WAITING, t);
		em.flush();
		em.clear();

		assertThat(first.getId()).isLessThan(second.getId());
		assertThat(ahead(first)).isZero();
		assertThat(ahead(second)).isEqualTo(1);
	}

	@Test
	void 재신청_행은_id가_작아도_신청_시각이_늦으면_기존_대기자_뒤다() {
		Course course = courseRepository.save(Course.create("자바 기초", "입문", "개발"));
		CourseSession s1 = session(course, 1);
		Instant t = Instant.parse("2026-11-16T01:00:00Z");
		Enrollment reapplied = enrollment(user("a@eduhub.local"), s1, EnrollmentStatus.CANCELLED, t);
		Enrollment w1 = enrollment(user("b@eduhub.local"), s1, EnrollmentStatus.WAITING, t.plusSeconds(1));
		Enrollment w2 = enrollment(user("c@eduhub.local"), s1, EnrollmentStatus.WAITING, t.plusSeconds(2));
		reapplied.reapply(false, t.plusSeconds(3));
		em.flush();
		em.clear();

		assertThat(reapplied.getId()).isLessThan(w1.getId());
		assertThat(ahead(w1)).isZero();
		assertThat(ahead(w2)).isEqualTo(1);
		assertThat(ahead(reapplied)).isEqualTo(2);
	}

	@Test
	void 확정_취소_다른_회차의_신청은_앞선_수에_세지_않는다() {
		Course course = courseRepository.save(Course.create("자바 기초", "입문", "개발"));
		CourseSession s1 = session(course, 1);
		CourseSession s2 = session(course, 2);
		Instant t = Instant.parse("2026-11-16T01:00:00Z");
		enrollment(user("a@eduhub.local"), s1, EnrollmentStatus.CONFIRMED, t);
		enrollment(user("b@eduhub.local"), s1, EnrollmentStatus.CANCELLED, t.plusSeconds(1));
		enrollment(user("c@eduhub.local"), s2, EnrollmentStatus.WAITING, t.plusSeconds(2));
		Enrollment mine = enrollment(user("d@eduhub.local"), s1, EnrollmentStatus.WAITING, t.plusSeconds(3));
		em.flush();
		em.clear();

		assertThat(ahead(mine)).isZero();
	}

	private Optional<Enrollment> nextToPromote(CourseSession session) {
		return enrollmentRepository.findFirstBySessionIdAndStatusOrderByAppliedAtAscIdAsc(
				session.getId(), EnrollmentStatus.WAITING);
	}

	@Test
	void 승급_대상은_신청_시각이_가장_이른_대기자이다() {
		Course course = courseRepository.save(Course.create("자바 기초", "입문", "개발"));
		CourseSession s1 = session(course, 1);
		Instant t = Instant.parse("2026-11-16T01:00:00Z");
		enrollment(user("a@eduhub.local"), s1, EnrollmentStatus.WAITING, t.plusSeconds(2));
		Enrollment earliest = enrollment(user("b@eduhub.local"), s1, EnrollmentStatus.WAITING, t);
		enrollment(user("c@eduhub.local"), s1, EnrollmentStatus.WAITING, t.plusSeconds(1));
		em.flush();
		em.clear();

		assertThat(nextToPromote(s1)).get().extracting(Enrollment::getId).isEqualTo(earliest.getId());
	}

	@Test
	void 승급_대상은_같은_시각이면_id가_작은_쪽이다() {
		Course course = courseRepository.save(Course.create("자바 기초", "입문", "개발"));
		CourseSession s1 = session(course, 1);
		Instant t = Instant.parse("2026-11-16T01:00:00Z");
		Enrollment first = enrollment(user("a@eduhub.local"), s1, EnrollmentStatus.WAITING, t);
		enrollment(user("b@eduhub.local"), s1, EnrollmentStatus.WAITING, t);
		em.flush();
		em.clear();

		assertThat(nextToPromote(s1)).get().extracting(Enrollment::getId).isEqualTo(first.getId());
	}

	@Test
	void 승급_대상에서_재신청_행은_id가_작아도_신청_시각이_늦으면_뒤다() {
		Course course = courseRepository.save(Course.create("자바 기초", "입문", "개발"));
		CourseSession s1 = session(course, 1);
		Instant t = Instant.parse("2026-11-16T01:00:00Z");
		Enrollment reapplied = enrollment(user("a@eduhub.local"), s1, EnrollmentStatus.CANCELLED, t);
		Enrollment w1 = enrollment(user("b@eduhub.local"), s1, EnrollmentStatus.WAITING, t.plusSeconds(1));
		reapplied.reapply(false, t.plusSeconds(3));
		em.flush();
		em.clear();

		assertThat(reapplied.getId()).isLessThan(w1.getId());
		assertThat(nextToPromote(s1)).get().extracting(Enrollment::getId).isEqualTo(w1.getId());
	}

	@Test
	void 승급_대상에서_확정과_다른_회차는_제외하고_대기가_없으면_비어_있다() {
		Course course = courseRepository.save(Course.create("자바 기초", "입문", "개발"));
		CourseSession s1 = session(course, 1);
		CourseSession s2 = session(course, 2);
		Instant t = Instant.parse("2026-11-16T01:00:00Z");
		enrollment(user("a@eduhub.local"), s1, EnrollmentStatus.CONFIRMED, t);
		enrollment(user("b@eduhub.local"), s1, EnrollmentStatus.CANCELLED, t.plusSeconds(1));
		Enrollment other = enrollment(user("c@eduhub.local"), s2, EnrollmentStatus.WAITING, t.plusSeconds(2));
		em.flush();
		em.clear();

		assertThat(nextToPromote(s1)).isEmpty();
		assertThat(nextToPromote(s2)).get().extracting(Enrollment::getId).isEqualTo(other.getId());
	}

	@Test
	void 잠금_조회로_회차가_조회된다() {
		Course course = courseRepository.save(Course.create("자바 기초", "입문", "개발"));
		CourseSession s1 = session(course, 1);
		em.flush();
		em.clear();

		assertThat(courseSessionRepository.findByIdForUpdate(s1.getId())).isPresent();
		assertThat(courseSessionRepository.findByIdForUpdate(-1L)).isEmpty();
	}
}
