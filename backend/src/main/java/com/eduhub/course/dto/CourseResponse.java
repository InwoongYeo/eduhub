package com.eduhub.course.dto;

import java.time.LocalDate;

import com.eduhub.course.domain.Course;
import com.eduhub.course.domain.CourseStatus;

/**
 * 강의 응답. 엔티티를 직접 노출하지 않는다 (NFR-06).
 */
public record CourseResponse(
		Long id,
		String title,
		String description,
		String instructorName,
		int capacity,
		LocalDate startDate,
		CourseStatus status) {

	public static CourseResponse from(Course course) {
		return new CourseResponse(
				course.getId(),
				course.getTitle(),
				course.getDescription(),
				course.getInstructor().getName(),
				course.getCapacity(),
				course.getStartDate(),
				course.getStatus());
	}
}
