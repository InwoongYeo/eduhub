package com.eduhub.course.repository;

import java.util.Optional;

import com.eduhub.course.domain.Course;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 과정 목록은 연관관계가 없는 단일 테이블 조회라 N+1이 생기지 않는다 (NFR-02).
 * 검색 조건은 CourseSpecifications로 조합한다.
 */
public interface CourseRepository extends JpaRepository<Course, Long>, JpaSpecificationExecutor<Course> {

	/**
	 * 과정 행을 쓰기 잠금(SELECT ... FOR UPDATE)으로 조회한다. 같은 과정의 관리 작업(수정·종료·회차 추가)을 직렬화한다.
	 * 잠금 순서는 과정 → 회차다 (D-32). 트랜잭션 안에서 호출해야 한다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from Course c where c.id = :id")
	Optional<Course> findByIdForUpdate(@Param("id") Long id);
}
