package com.eduhub.course.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.eduhub.course.domain.Course;
import com.eduhub.course.domain.CourseSession;
import com.eduhub.course.domain.CourseStatus;
import com.eduhub.user.domain.Role;
import com.eduhub.user.domain.User;
import com.eduhub.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/**
 * Flyway V1·V2로 스키마를 만들고 ddl-auto=validate로 엔티티 매핑을 검증한다.
 * Docker 없이 H2(MySQL 모드)로 실행한다.
 */
@DataJpaTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:jpatest;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CourseSessionRepositoryTest {

	@Autowired
	UserRepository userRepository;

	@Autowired
	CourseRepository courseRepository;

	@Autowired
	CourseSessionRepository courseSessionRepository;

	@Autowired
	EntityManager em;

	@Test
	void 회차는_회차_번호_순으로_조회되고_강의_폐강_상태가_저장된다() {
		User instructor = userRepository.save(User.create("t@eduhub.local", "encoded", "김강사", Role.INSTRUCTOR));
		Course course = courseRepository.save(
				Course.create("자바 기초", "입문", instructor, 30, LocalDate.of(2026, 12, 1)));
		LocalDateTime base = LocalDateTime.of(2026, 12, 1, 19, 0);
		courseSessionRepository.save(CourseSession.create(course, 2, "객체지향", base.plusWeeks(1), base.plusWeeks(1).plusHours(2)));
		courseSessionRepository.save(CourseSession.create(course, 1, "문법", base, base.plusHours(2)));

		course.close();
		em.flush();
		em.clear();

		List<CourseSession> sessions = courseSessionRepository.findByCourseIdOrderBySessionNoAsc(course.getId());
		assertThat(sessions).extracting(CourseSession::getSessionNo).containsExactly(1, 2);
		assertThat(sessions.get(0).getTitle()).isEqualTo("문법");
		assertThat(courseRepository.findById(course.getId()).orElseThrow().getStatus())
				.isEqualTo(CourseStatus.CLOSED);
	}
}
