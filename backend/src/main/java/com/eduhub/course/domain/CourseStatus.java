package com.eduhub.course.domain;

/**
 * 강의 상태. 수강생이 있는 강의는 삭제하지 않고 CLOSED(폐강)로 바꾼다 (D-03).
 */
public enum CourseStatus {
	RECRUITING,
	CLOSED
}
