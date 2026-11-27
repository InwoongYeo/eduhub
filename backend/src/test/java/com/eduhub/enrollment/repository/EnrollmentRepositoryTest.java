package com.eduhub.enrollment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
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

	private List<MyEnrollmentRow> mine(User user, EnrollmentStatus status) {
		return enrollmentRepository.findMyEnrollments(user.getId(), status);
	}

	@Test
	void 내_신청_내역에는_본인_행만_나온다() {
		Course course = courseRepository.save(Course.create("자바 기초", "입문", "개발"));
		CourseSession s1 = session(course, 1);
		CourseSession s2 = session(course, 2);
		Instant t = Instant.parse("2026-11-16T01:00:00Z");
		User a = user("a@eduhub.local");
		User b = user("b@eduhub.local");
		Enrollment a1 = enrollment(a, s1, EnrollmentStatus.CONFIRMED, t);
		Enrollment a2 = enrollment(a, s2, EnrollmentStatus.CONFIRMED, t.plusSeconds(1));
		enrollment(b, s1, EnrollmentStatus.CONFIRMED, t.plusSeconds(2));
		enrollment(b, s2, EnrollmentStatus.WAITING, t.plusSeconds(3));
		em.flush();
		em.clear();

		assertThat(mine(a, null)).extracting(MyEnrollmentRow::enrollmentId)
				.containsExactlyInAnyOrder(a1.getId(), a2.getId());
	}

	@Test
	void 내_신청_내역은_신청_시각_내림차순이고_같으면_id_내림차순이다() {
		Course course = courseRepository.save(Course.create("자바 기초", "입문", "개발"));
		User a = user("a@eduhub.local");
		Instant t = Instant.parse("2026-11-16T01:00:00Z");
		Enrollment oldest = enrollment(a, session(course, 1), EnrollmentStatus.CONFIRMED, t);
		Enrollment tieLow = enrollment(a, session(course, 2), EnrollmentStatus.CONFIRMED, t.plusSeconds(5));
		Enrollment tieHigh = enrollment(a, session(course, 3), EnrollmentStatus.CONFIRMED, t.plusSeconds(5));
		Enrollment newest = enrollment(a, session(course, 4), EnrollmentStatus.CONFIRMED, t.plusSeconds(9));
		em.flush();
		em.clear();

		assertThat(tieLow.getId()).isLessThan(tieHigh.getId());
		assertThat(mine(a, null)).extracting(MyEnrollmentRow::enrollmentId)
				.containsExactly(newest.getId(), tieHigh.getId(), tieLow.getId(), oldest.getId());
	}

	@Test
	void 내_신청_내역은_상태로_거르고_null이면_전체이다() {
		Course course = courseRepository.save(Course.create("자바 기초", "입문", "개발"));
		User a = user("a@eduhub.local");
		Instant t = Instant.parse("2026-11-16T01:00:00Z");
		enrollment(a, session(course, 1), EnrollmentStatus.CONFIRMED, t);
		Enrollment waiting = enrollment(a, session(course, 2), EnrollmentStatus.WAITING, t.plusSeconds(1));
		enrollment(a, session(course, 3), EnrollmentStatus.CANCELLED, t.plusSeconds(2));
		em.flush();
		em.clear();

		assertThat(mine(a, EnrollmentStatus.WAITING)).extracting(MyEnrollmentRow::enrollmentId)
				.containsExactly(waiting.getId());
		assertThat(mine(a, null)).hasSize(3);
	}

	@Test
	void 내_신청_내역의_대기_순번은_countWaitingAhead_더하기_1과_같고_그_밖은_null이다() {
		Course course = courseRepository.save(Course.create("자바 기초", "입문", "개발"));
		CourseSession s1 = session(course, 1);
		CourseSession s2 = session(course, 2);
		Instant t = Instant.parse("2026-11-16T01:00:00Z");
		User confirmedUser = user("c@eduhub.local");
		User cancelledUser = user("x@eduhub.local");
		User w1User = user("w1@eduhub.local");
		User w2User = user("w2@eduhub.local");
		User w3User = user("w3@eduhub.local");
		enrollment(confirmedUser, s1, EnrollmentStatus.CONFIRMED, t);
		enrollment(cancelledUser, s1, EnrollmentStatus.CANCELLED, t.plusSeconds(1));
		// 같은 시각 두 명 + 더 늦은 한 명. 다른 회차 대기자는 세지 않는다
		Enrollment w1 = enrollment(w1User, s1, EnrollmentStatus.WAITING, t.plusSeconds(2));
		Enrollment w2 = enrollment(w2User, s1, EnrollmentStatus.WAITING, t.plusSeconds(2));
		Enrollment w3 = enrollment(w3User, s1, EnrollmentStatus.WAITING, t.plusSeconds(3));
		enrollment(user("o@eduhub.local"), s2, EnrollmentStatus.WAITING, t);
		em.flush();
		em.clear();

		for (Enrollment w : List.of(w1, w2, w3)) {
			User owner = userRepository.findById(w.getEmployee().getId()).orElseThrow();
			List<MyEnrollmentRow> rows = mine(owner, null);
			assertThat(rows).hasSize(1);
			assertThat(rows.get(0).waitingNo()).isEqualTo(ahead(w) + 1);
		}
		assertThat(mine(w1User, null).get(0).waitingNo()).isEqualTo(1L);
		assertThat(mine(w2User, null).get(0).waitingNo()).isEqualTo(2L);
		assertThat(mine(w3User, null).get(0).waitingNo()).isEqualTo(3L);
		assertThat(mine(confirmedUser, null).get(0).waitingNo()).isNull();
		assertThat(mine(cancelledUser, null).get(0).waitingNo()).isNull();
	}

	@Test
	void 내_신청_내역의_필드는_과정과_회차_값과_같다() {
		Course course = courseRepository.save(Course.create("자바 기초", "입문", "개발"));
		CourseSession s1 = session(course, 1);
		User a = user("a@eduhub.local");
		Instant t = Instant.parse("2026-11-16T01:00:00Z");
		Enrollment e = enrollment(a, s1, EnrollmentStatus.CONFIRMED, t);
		em.flush();
		em.clear();

		MyEnrollmentRow row = mine(a, null).get(0);
		assertThat(row.enrollmentId()).isEqualTo(e.getId());
		assertThat(row.sessionId()).isEqualTo(s1.getId());
		assertThat(row.courseTitle()).isEqualTo("자바 기초");
		assertThat(row.sessionStartsAt()).isEqualTo(s1.getStartsAt());
		assertThat(row.location()).isEqualTo("본사");
		assertThat(row.status()).isEqualTo(EnrollmentStatus.CONFIRMED);
		assertThat(row.appliedAt()).isEqualTo(t);
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
