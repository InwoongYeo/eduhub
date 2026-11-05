package com.eduhub.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.eduhub.course.domain.Course;
import com.eduhub.course.dto.CourseResponse;
import com.eduhub.course.repository.CourseRepository;
import com.eduhub.global.error.BusinessException;
import com.eduhub.global.error.ErrorCode;
import com.eduhub.user.domain.Role;
import com.eduhub.user.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

	@Mock
	CourseRepository courseRepository;

	@InjectMocks
	CourseService courseService;

	private final User instructor = User.create("t@eduhub.local", "encoded", "김강사", Role.INSTRUCTOR);
	private final Course course = Course.create("자바 기초", "입문", instructor, 30, LocalDate.now().plusDays(7));

	@Test
	void 검색어가_없으면_전체_목록을_페이지로_조회한다() {
		Pageable pageable = PageRequest.of(0, 10);
		given(courseRepository.findAll(pageable)).willReturn(new PageImpl<>(List.of(course), pageable, 1));

		Page<CourseResponse> result = courseService.getCourses("  ", pageable);

		assertThat(result.getTotalElements()).isEqualTo(1);
		assertThat(result.getContent().get(0).instructorName()).isEqualTo("김강사");
		verify(courseRepository, never())
				.findByTitleContainingOrInstructorNameContaining(any(), any(), any());
	}

	@Test
	void 검색어가_있으면_제목과_강사_이름으로_검색한다() {
		Pageable pageable = PageRequest.of(0, 10);
		given(courseRepository.findByTitleContainingOrInstructorNameContaining("자바", "자바", pageable))
				.willReturn(new PageImpl<>(List.of(course), pageable, 1));

		Page<CourseResponse> result = courseService.getCourses(" 자바 ", pageable);

		assertThat(result.getContent()).extracting(CourseResponse::title).containsExactly("자바 기초");
	}

	@Test
	void 없는_강의를_조회하면_COURSE_NOT_FOUND_예외가_난다() {
		given(courseRepository.findById(999L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> courseService.getCourse(999L))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.COURSE_NOT_FOUND);
	}
}
