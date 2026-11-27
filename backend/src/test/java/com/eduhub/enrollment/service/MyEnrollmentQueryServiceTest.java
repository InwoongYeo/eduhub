package com.eduhub.enrollment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import com.eduhub.enrollment.domain.EnrollmentStatus;
import com.eduhub.enrollment.dto.MyEnrollmentResponse;
import com.eduhub.enrollment.repository.EnrollmentRepository;
import com.eduhub.enrollment.repository.MyEnrollmentRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MyEnrollmentQueryServiceTest {

	@Mock
	EnrollmentRepository enrollmentRepository;

	@InjectMocks
	MyEnrollmentQueryService service;

	private final LocalDateTime startsAt = LocalDateTime.of(2099, 1, 10, 9, 0);
	private final Instant appliedAt = Instant.parse("2026-11-26T01:00:00Z");

	@Test
	void 리포지토리를_한_번만_호출하고_인자를_그대로_넘긴다() {
		when(enrollmentRepository.findMyEnrollments(7L, EnrollmentStatus.WAITING)).thenReturn(List.of());

		service.findMine(7L, EnrollmentStatus.WAITING);

		verify(enrollmentRepository).findMyEnrollments(7L, EnrollmentStatus.WAITING);
		verifyNoMoreInteractions(enrollmentRepository);
	}

	@Test
	void 대기_순번_Long을_Integer로_바꾸고_null은_유지한다() {
		MyEnrollmentRow waiting = new MyEnrollmentRow(
				1L, 10L, "자바 기초", startsAt, "본사", EnrollmentStatus.WAITING, appliedAt, 3L);
		MyEnrollmentRow confirmed = new MyEnrollmentRow(
				2L, 11L, "스프링", startsAt, "별관", EnrollmentStatus.CONFIRMED, appliedAt, null);
		when(enrollmentRepository.findMyEnrollments(7L, null)).thenReturn(List.of(waiting, confirmed));

		List<MyEnrollmentResponse> result = service.findMine(7L, null);

		assertThat(result).hasSize(2);
		assertThat(result.get(0)).isEqualTo(new MyEnrollmentResponse(
				1L, 10L, "자바 기초", startsAt, "본사", EnrollmentStatus.WAITING, appliedAt, 3));
		assertThat(result.get(1).waitingNo()).isNull();
		assertThat(result.get(1).courseTitle()).isEqualTo("스프링");
	}

	@Test
	void 결과가_없으면_빈_목록이다() {
		when(enrollmentRepository.findMyEnrollments(7L, null)).thenReturn(List.of());

		assertThat(service.findMine(7L, null)).isEmpty();
	}
}
