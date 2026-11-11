package com.eduhub.course.repository;

/**
 * 과정별 회차 수 집계 결과.
 */
public record SessionCount(Long courseId, long count) {
}
