package com.eduhub.course.domain;

import java.time.LocalDate;

import com.eduhub.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "courses", indexes = @Index(name = "idx_course_title", columnList = "title")) // D-05, NFR-05
public class Course {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(length = 1000)
	private String description;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "instructor_id", nullable = false)
	private User instructor;

	/** FR-07: 최대 수강 인원 */
	@Column(nullable = false)
	private int capacity;

	/** D-02: 이 날짜 전까지만 수강 취소 가능 */
	@Column(nullable = false)
	private LocalDate startDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private CourseStatus status;

	protected Course() {
	}

	private Course(String title, String description, User instructor, int capacity, LocalDate startDate) {
		this.title = title;
		this.description = description;
		this.instructor = instructor;
		this.capacity = capacity;
		this.startDate = startDate;
		this.status = CourseStatus.RECRUITING;
	}

	public static Course create(String title, String description, User instructor, int capacity, LocalDate startDate) {
		return new Course(title, description, instructor, capacity, startDate);
	}

	public void close() {
		this.status = CourseStatus.CLOSED;
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public User getInstructor() {
		return instructor;
	}

	public int getCapacity() {
		return capacity;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public CourseStatus getStatus() {
		return status;
	}
}
