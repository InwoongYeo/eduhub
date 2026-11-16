package com.eduhub.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.eduhub.course.domain.CourseStatus;
import com.eduhub.course.domain.SessionStatus;
import com.eduhub.course.dto.CourseRequest;
import com.eduhub.course.dto.CourseResponse;
import com.eduhub.course.dto.SessionRequest;
import com.eduhub.course.dto.SessionResponse;
import com.eduhub.course.service.CourseAdminService;
import com.eduhub.global.error.BusinessException;
import com.eduhub.global.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 과정 단위 관리 작업의 잠금 동시성 테스트 (D-32). 실제 MySQL 8.4에서 과정 행 비관적 락이
 * 종료·수정·회차 추가를 직렬화하는지 확인한다. 락이 없으면 교착, 종료 덮어쓰기, 회차 번호 중복이 생긴다.
 * 클래스에 @Transactional을 붙이지 않는다. 스레드마다 서비스의 트랜잭션이 실제로 커밋돼야 하기 때문이다.
 * Docker가 없으면 실패한다 (건너뛰지 않음).
 * 경쟁은 확률적이라 라운드를 반복한다(락을 빼면 A1~A3 중 하나 이상이 실패해야 한다).
 * 라운드마다 과정을 새로 만들고 tearDown이 그 과정만 지운다.
 */
@SpringBootTest
@Testcontainers
class CourseAdminConcurrencyTest {

	private static final int ROUNDS = 10;
	private static final int CONCURRENT_TASKS = 10;

	@Container
	@ServiceConnection
	// 이 환경에서는 MySQL 초기화가 2분 이상 걸려 기본 대기 120초를 넘는다. 데이터 디렉터리를 tmpfs로 두어 단축하고 대기를 300초로 늘린다.
	static MySQLContainer mysql = new MySQLContainer("mysql:8.4")
			.withTmpFs(Map.of("/var/lib/mysql", "rw"))
			.withStartupTimeoutSeconds(300)
			.withConnectTimeoutSeconds(300);

	@Autowired
	private CourseAdminService courseAdminService;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	/** 테스트가 만든 과정 ID. LocalDataInitializer의 데이터는 포함하지 않는다. */
	private final List<Long> courseIds = new ArrayList<>();

	@AfterEach
	void tearDown() {
		for (Long courseId : courseIds) {
			jdbcTemplate.update("DELETE FROM course_session WHERE course_id = ?", courseId);
			jdbcTemplate.update("DELETE FROM course WHERE id = ?", courseId);
		}
		courseIds.clear();
	}

	private Long createCourse() {
		CourseResponse course = courseAdminService.createCourse(new CourseRequest("과정 잠금 테스트", "동시성 테스트용", "TEST"));
		courseIds.add(course.id());
		return course.id();
	}

	private SessionRequest sessionRequest() {
		return new SessionRequest(LocalDateTime.of(2099, 1, 1, 10, 0), LocalDateTime.of(2099, 1, 1, 18, 0), "대강당", 10);
	}

	/** 모든 작업을 동시에 출발시키고 결과를 모은다. 예외도 결과로 담는다 (정상 종료는 null 또는 반환값). */
	private List<Object> runConcurrently(List<Callable<Object>> tasks) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
		try {
			CountDownLatch ready = new CountDownLatch(tasks.size());
			CountDownLatch start = new CountDownLatch(1);
			List<Future<Object>> futures = new ArrayList<>();
			for (Callable<Object> task : tasks) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					try {
						return task.call();
					} catch (Throwable t) {
						return t;
					}
				}));
			}
			if (!ready.await(30, TimeUnit.SECONDS)) {
				fail("작업 스레드가 30초 안에 출발 대기 상태가 되지 않았다");
			}
			start.countDown();

			List<Object> results = new ArrayList<>();
			for (Future<Object> future : futures) {
				results.add(future.get(30, TimeUnit.SECONDS));
			}
			return results;
		} finally {
			executor.shutdownNow();
		}
	}

	private Callable<Object> closeCourseTask(Long courseId) {
		return () -> {
			courseAdminService.closeCourse(courseId);
			return null;
		};
	}

	private Callable<Object> addSessionTask(Long courseId) {
		return () -> courseAdminService.addSession(courseId, sessionRequest());
	}

	private Callable<Object> updateCourseTask(Long courseId, int index) {
		return () -> courseAdminService.updateCourse(courseId,
				new CourseRequest("수정된 제목-" + index, "수정", "TEST"));
	}

	/** 예외 중 BusinessException(COURSE_CLOSED)이 아닌 것만 모은다. 비어 있어야 한다. */
	private List<Object> unexpectedFailures(List<Object> results) {
		return results.stream()
				.filter(r -> r instanceof Throwable)
				.filter(r -> !(r instanceof BusinessException e && e.getErrorCode() == ErrorCode.COURSE_CLOSED))
				.toList();
	}

	private String courseStatusOf(Long courseId) {
		return jdbcTemplate.queryForObject("SELECT status FROM course WHERE id = ?", String.class, courseId);
	}

	@Test
	@DisplayName("A1: 과정 종료 1건과 회차 추가 10건이 동시에 실행되면 COURSE_CLOSED 외 예외가 없고, 과정과 모든 회차가 종료된다 (10라운드)")
	void 과정_종료와_회차_추가가_동시에_실행되면_모든_회차가_종료된다() throws Exception {
		for (int round = 1; round <= ROUNDS; round++) {
			Long courseId = createCourse();
			// 종료가 먼저 커밋돼도 검증할 회차가 있도록 동시 실행 전에 회차 1개를 미리 만든다
			courseAdminService.addSession(courseId, sessionRequest());
			List<Callable<Object>> tasks = new ArrayList<>();
			tasks.add(closeCourseTask(courseId));
			for (int i = 0; i < CONCURRENT_TASKS; i++) {
				tasks.add(addSessionTask(courseId));
			}

			List<Object> results = runConcurrently(tasks);

			assertThat(unexpectedFailures(results)).as("라운드 %d COURSE_CLOSED 외 예외", round).isEmpty();
			assertThat(courseStatusOf(courseId)).as("라운드 %d 과정 상태", round).isEqualTo(CourseStatus.CLOSED.name());
			long succeededAdds = results.stream()
					.filter(r -> r instanceof SessionResponse)
					.count();
			Integer totalSessions = jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM course_session WHERE course_id = ?", Integer.class, courseId);
			assertThat(totalSessions).as("라운드 %d 회차 수 (정상 추가 + 미리 만든 1개)", round)
					.isEqualTo((int) succeededAdds + 1);
			Integer notClosed = jdbcTemplate.queryForObject(
					"SELECT COUNT(*) FROM course_session WHERE course_id = ? AND status <> ?",
					Integer.class, courseId, SessionStatus.CLOSED.name());
			assertThat(notClosed).as("라운드 %d 종료되지 않은 회차 수", round).isZero();
		}
	}

	@Test
	@DisplayName("A2: 과정 종료 1건과 과정 수정 10건이 동시에 실행되면 COURSE_CLOSED 외 예외가 없고, 과정은 종료 상태다 (10라운드)")
	void 과정_종료와_과정_수정이_동시에_실행돼도_과정이_종료_상태로_남는다() throws Exception {
		for (int round = 1; round <= ROUNDS; round++) {
			Long courseId = createCourse();
			List<Callable<Object>> tasks = new ArrayList<>();
			tasks.add(closeCourseTask(courseId));
			for (int i = 0; i < CONCURRENT_TASKS; i++) {
				tasks.add(updateCourseTask(courseId, i));
			}

			List<Object> results = runConcurrently(tasks);

			assertThat(unexpectedFailures(results)).as("라운드 %d COURSE_CLOSED 외 예외", round).isEmpty();
			assertThat(courseStatusOf(courseId)).as("라운드 %d 과정 상태", round).isEqualTo(CourseStatus.CLOSED.name());
		}
	}

	@Test
	@DisplayName("A3: 회차 추가 10건이 동시에 실행되면 예외가 없고 회차 번호가 정확히 1~10이다 (10라운드)")
	void 회차_추가가_동시에_실행되면_회차_번호가_중복_없이_1부터_10이다() throws Exception {
		for (int round = 1; round <= ROUNDS; round++) {
			Long courseId = createCourse();
			List<Callable<Object>> tasks = new ArrayList<>();
			for (int i = 0; i < CONCURRENT_TASKS; i++) {
				tasks.add(addSessionTask(courseId));
			}

			List<Object> results = runConcurrently(tasks);

			assertThat(results.stream().filter(r -> r instanceof Throwable).toList())
					.as("라운드 %d 예외", round).isEmpty();
			List<Integer> sessionNos = jdbcTemplate.queryForList(
					"SELECT session_no FROM course_session WHERE course_id = ? ORDER BY session_no",
					Integer.class, courseId);
			assertThat(sessionNos).as("라운드 %d 회차 번호", round)
					.containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
		}
	}
}
