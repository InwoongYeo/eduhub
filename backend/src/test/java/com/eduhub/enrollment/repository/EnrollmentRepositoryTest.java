package com.eduhub.enrollment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

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
