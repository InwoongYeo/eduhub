package com.eduhub.global.init;

import java.time.LocalDate;
import java.util.UUID;

import com.eduhub.course.domain.Course;
import com.eduhub.course.repository.CourseRepository;
import com.eduhub.user.domain.Role;
import com.eduhub.user.domain.User;
import com.eduhub.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * local 프로필에서만 관리자와 샘플 강의를 만든다 (D-04).
 */
@Component
@Profile("local")
public class LocalDataInitializer implements ApplicationRunner {

	private final UserRepository userRepository;
	private final CourseRepository courseRepository;
	private final PasswordEncoder passwordEncoder;
	private final String adminEmail;
	private final String adminPassword;

	public LocalDataInitializer(UserRepository userRepository,
			CourseRepository courseRepository,
			PasswordEncoder passwordEncoder,
			@Value("${eduhub.admin.email}") String adminEmail,
			@Value("${eduhub.admin.password}") String adminPassword) {
		this.userRepository = userRepository;
		this.courseRepository = courseRepository;
		this.passwordEncoder = passwordEncoder;
		this.adminEmail = adminEmail;
		this.adminPassword = adminPassword;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (userRepository.existsByEmail(adminEmail)) {
			return;
		}
		userRepository.save(User.create(adminEmail, passwordEncoder.encode(adminPassword), "관리자", Role.ADMIN));

		// 샘플 강사: 1주차에는 로그인이 없으므로 임의 비밀번호를 쓴다
		User instructor = userRepository.save(User.create(
				"instructor@eduhub.local",
				passwordEncoder.encode(UUID.randomUUID().toString()),
				"김강사",
				Role.INSTRUCTOR));

		LocalDate today = LocalDate.now();
		courseRepository.save(Course.create("자바 기초", "Java 문법과 객체지향 입문", instructor, 30, today.plusWeeks(2)));
		courseRepository.save(Course.create("스프링 부트 입문", "REST API 만들기", instructor, 20, today.plusWeeks(4)));
		courseRepository.save(Course.create("데이터베이스 설계", "ERD와 정규화", instructor, 25, today.plusWeeks(6)));
	}
}
