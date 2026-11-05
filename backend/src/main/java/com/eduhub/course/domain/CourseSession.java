package com.eduhub.course.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 강의의 수업 회차 (D-11). 강의 하나에 여러 회차가 있고, 회차 번호는 강의 안에서 유일하다.
 */
@Entity
@Table(name = "course_session",
		uniqueConstraints = @UniqueConstraint(name = "uk_course_session_no", columnNames = {"course_id", "session_no"}))
public class CourseSession {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "course_id", nullable = false)
	private Course course;

	@Column(name = "session_no", nullable = false)
	private int sessionNo;

	@Column(nullable = false, length = 100)
	private String title;

	@Column(nullable = false)
	private LocalDateTime startsAt;

	@Column(nullable = false)
	private LocalDateTime endsAt;

	protected CourseSession() {
	}

	private CourseSession(Course course, int sessionNo, String title, LocalDateTime startsAt, LocalDateTime endsAt) {
		if (!endsAt.isAfter(startsAt)) {
			throw new IllegalArgumentException("회차 종료 시각은 시작 시각보다 뒤여야 합니다.");
		}
		this.course = course;
		this.sessionNo = sessionNo;
		this.title = title;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
	}

	public static CourseSession create(Course course, int sessionNo, String title,
			LocalDateTime startsAt, LocalDateTime endsAt) {
		return new CourseSession(course, sessionNo, title, startsAt, endsAt);
	}

	public Long getId() {
		return id;
	}

	public Course getCourse() {
		return course;
	}

	public int getSessionNo() {
		return sessionNo;
	}

	public String getTitle() {
		return title;
	}

	public LocalDateTime getStartsAt() {
		return startsAt;
	}

	public LocalDateTime getEndsAt() {
		return endsAt;
	}
}
