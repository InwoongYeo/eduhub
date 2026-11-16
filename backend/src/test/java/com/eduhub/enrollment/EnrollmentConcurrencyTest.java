package com.eduhub.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import com.eduhub.course.domain.Course;
import com.eduhub.course.domain.CourseSession;
import com.eduhub.course.dto.SessionRequest;
import com.eduhub.course.repository.CourseRepository;
import com.eduhub.course.repository.CourseSessionRepository;
import com.eduhub.course.service.CourseAdminService;
import com.eduhub.enrollment.domain.Enrollment;
import com.eduhub.enrollment.domain.EnrollmentStatus;
import com.eduhub.enrollment.dto.EnrollmentResponse;
import com.eduhub.enrollment.repository.EnrollmentRepository;
import com.eduhub.enrollment.service.EnrollmentService;
import com.eduhub.global.error.BusinessException;
import com.eduhub.global.error.ErrorCode;
import com.eduhub.user.domain.Role;
import com.eduhub.user.domain.User;
import com.eduhub.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 수강 신청 동시성 테스트 (NFR-01). 실제 MySQL 8.4에서 회차 행 비관적 락이 정원을 지키는지 확인한다.
 * 클래스에 @Transactional을 붙이지 않는다. 스레드마다 서비스의 트랜잭션이 실제로 커밋돼야 하기 때문이다.
 * Docker가 없으면 실패한다 (건너뛰지 않음, 설계 Q2).
 * 풀이 32라 33번째 이후 작업은 완전한 동시 출발이 아니지만, 모두 같은 회차 행 락에 몰려 경합이 생긴다.
 */
@SpringBootTest
@Testcontainers
class EnrollmentConcurrencyTest {

	private static final int USERS = 100;
	private static final int THREADS = 32;
	private static final int N5_ROUNDS = 5;
	private static final int N5_ADMIN_UPDATES = 50;

	@Container
	@ServiceConnection
	// 이 환경에서는 MySQL 초기화가 2분 이상 걸려 기본 대기 120초를 넘는다. 데이터 디렉터리를 tmpfs로 두어 단축하고 대기를 300초로 늘린다.
	static MySQLContainer mysql = new MySQLContainer("mysql:8.4")
			.withTmpFs(Map.of("/var/lib/mysql", "rw"))
			.withStartupTimeoutSeconds(300)
			.withConnectTimeoutSeconds(300);

	@Autowired
	private EnrollmentService enrollmentService;
	@Autowired
	private CourseAdminService courseAdminService;
	@Autowired
	private CourseRepository courseRepository;
	@Autowired
	private CourseSessionRepository courseSessionRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private EnrollmentRepository enrollmentRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Long courseId;
	private List<Long> userIds;

	@BeforeEach
	void setUp() {
		Course course = courseRepository.save(Course.create("동시성 테스트 과정", "부하 테스트용", "TEST"));
		courseId = course.getId();

		List<User> users = new ArrayList<>();
		for (int i = 0; i < USERS; i++) {
			users.add(User.create("load-" + i + "@test.local", "x", "부하" + i, Role.EMPLOYEE));
		}
		userIds = userRepository.saveAll(users).stream().map(User::getId).toList();
	}

	@AfterEach
	void tearDown() {
		// 테스트가 만든 행만 지운다. LocalDataInitializer의 기존 데이터는 건드리지 않는다.
		// setUp이 course 저장 전에 실패해 courseId가 null이면 삭제를 건너뛴다 (원래 오류가 가려지지 않게).
		if (courseId != null) {
			jdbcTemplate.update("DELETE FROM enrollment WHERE session_id IN (SELECT id FROM course_session WHERE course_id = ?)",
					courseId);
			jdbcTemplate.update("DELETE FROM course_session WHERE course_id = ?", courseId);
			jdbcTemplate.update("DELETE FROM course WHERE id = ?", courseId);
		}
		jdbcTemplate.update("DELETE FROM users WHERE email LIKE 'load-%@test.local'");
	}

	private Long createSession(int capacity) {
		Course course = courseRepository.findById(courseId).orElseThrow();
		int nextNo = courseSessionRepository.findMaxSessionNo(courseId) + 1;
		CourseSession session = CourseSession.create(course, nextNo,
				LocalDateTime.of(2099, 1, 1, 10, 0), LocalDateTime.of(2099, 1, 1, 18, 0), "대강당", capacity);
		return courseSessionRepository.save(session).getId();
	}

	/** 모든 작업을 동시에 출발시키고 결과를 모은다. 예외도 결과로 담는다. */
	private List<Object> runConcurrently(List<Callable<EnrollmentResponse>> tasks) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(THREADS);
		try {
			CountDownLatch ready = new CountDownLatch(Math.min(tasks.size(), THREADS));
			CountDownLatch start = new CountDownLatch(1);
			List<Future<Object>> futures = new ArrayList<>();
			for (Callable<EnrollmentResponse> task : tasks) {
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
			// 풀 크기만큼의 스레드가 대기 상태가 되면 한꺼번에 출발시킨다.
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

	private List<Callable<EnrollmentResponse>> tasksForAllUsers(Long sessionId) {
		List<Callable<EnrollmentResponse>> tasks = new ArrayList<>();
		for (Long userId : userIds) {
			tasks.add(() -> enrollmentService.enroll(sessionId, userId));
		}
		return tasks;
	}

	private long countStatus(List<Object> results, EnrollmentStatus status) {
		return results.stream()
				.filter(r -> r instanceof EnrollmentResponse response && response.status() == status)
				.count();
	}

	/** 서비스 반환값이 아니라 DB에 저장된 행 기준으로 상태별 수를 센다. */
	private int dbCountOf(Long sessionId, EnrollmentStatus status) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM enrollment WHERE session_id = ? AND status = ?",
				Integer.class, sessionId, status.name());
	}

	private int confirmedCountOf(Long sessionId) {
		return jdbcTemplate.queryForObject("SELECT confirmed_count FROM course_session WHERE id = ?",
				Integer.class, sessionId);
	}

	@Test
	@DisplayName("N1: 정원 10에 서로 다른 임직원 100명이 동시에 신청하면 확정 10, 대기 90이고 예외가 없다")
	void 정원_10에_100명이_동시에_신청하면_확정_10_대기_90() throws Exception {
		Long sessionId = createSession(10);

		List<Object> results = runConcurrently(tasksForAllUsers(sessionId));

		long exceptions = results.stream().filter(r -> r instanceof Throwable).count();
		assertThat(exceptions).isZero();
		assertThat(countStatus(results, EnrollmentStatus.CONFIRMED)).isEqualTo(10);
		assertThat(countStatus(results, EnrollmentStatus.WAITING)).isEqualTo(90);
		assertThat(dbCountOf(sessionId, EnrollmentStatus.CONFIRMED)).isEqualTo(10);
		assertThat(dbCountOf(sessionId, EnrollmentStatus.WAITING)).isEqualTo(90);
		assertThat(confirmedCountOf(sessionId)).isEqualTo(10);
		assertThat(waitingNosOf(results)).containsExactlyElementsOf(rangeClosed(1, 90));
	}

	@Test
	@DisplayName("N2: 정원 1(마지막 1자리)에 100명이 동시에 신청하면 확정 1, 대기 99이다")
	void 마지막_1자리에_100명이_동시에_신청하면_확정_1_대기_99() throws Exception {
		Long sessionId = createSession(1);

		List<Object> results = runConcurrently(tasksForAllUsers(sessionId));

		long exceptions = results.stream().filter(r -> r instanceof Throwable).count();
		assertThat(exceptions).isZero();
		assertThat(countStatus(results, EnrollmentStatus.CONFIRMED)).isEqualTo(1);
		assertThat(countStatus(results, EnrollmentStatus.WAITING)).isEqualTo(99);
		assertThat(dbCountOf(sessionId, EnrollmentStatus.CONFIRMED)).isEqualTo(1);
		assertThat(dbCountOf(sessionId, EnrollmentStatus.WAITING)).isEqualTo(99);
		assertThat(confirmedCountOf(sessionId)).isEqualTo(1);
		assertThat(waitingNosOf(results)).containsExactlyElementsOf(rangeClosed(1, 99));
	}

	/** 대기 응답의 순번을 오름차순으로 모은다 (FR-05). */
	private List<Integer> waitingNosOf(List<Object> results) {
		return results.stream()
				.filter(r -> r instanceof EnrollmentResponse response && response.status() == EnrollmentStatus.WAITING)
				.map(r -> ((EnrollmentResponse) r).waitingNo())
				.sorted()
				.toList();
	}

	private static List<Integer> rangeClosed(int from, int to) {
		return IntStream.rangeClosed(from, to).boxed().toList();
	}

	@Test
	@DisplayName("N7: 정원 1에 30명이 동시에 신청한 뒤 대기자 각각의 내 상태 조회 순번이 신청 응답의 순번과 같고 1~29이다")
	void 동시_신청_뒤_내_상태_조회_순번이_신청_응답과_같다() throws Exception {
		Long sessionId = createSession(1);
		int applicants = 30;
		List<Callable<EnrollmentResponse>> tasks = new ArrayList<>();
		for (int i = 0; i < applicants; i++) {
			Long userId = userIds.get(i);
			tasks.add(() -> enrollmentService.enroll(sessionId, userId));
		}

		List<Object> results = runConcurrently(tasks);

		assertThat(results.stream().filter(r -> r instanceof Throwable).count()).isZero();
		List<Integer> mineNos = new ArrayList<>();
		for (int i = 0; i < applicants; i++) {
			EnrollmentResponse applied = (EnrollmentResponse) results.get(i);
			EnrollmentResponse mine = enrollmentService.findMine(sessionId, userIds.get(i));
			assertThat(mine.status()).isEqualTo(applied.status());
			assertThat(mine.waitingNo()).isEqualTo(applied.waitingNo());
			if (mine.status() == EnrollmentStatus.WAITING) {
				mineNos.add(mine.waitingNo());
			}
		}
		assertThat(waitingNosOf(results)).containsExactlyElementsOf(rangeClosed(1, applicants - 1));
		assertThat(mineNos.stream().sorted().toList()).containsExactlyElementsOf(rangeClosed(1, applicants - 1));
	}

	/**
	 * 이 테스트는 락이 아니라 DB 유일 제약 안전망을 증명한다(락 없이도 통과).
	 * 새 행 INSERT 경합은 uk_enrollment_employee_session이 막는다.
	 */
	@Test
	@DisplayName("N3: 같은 임직원이 같은 회차에 10번 동시에 신청하면 1건만 성공하고 9건은 중복 오류다. 락이 아니라 DB 유일 제약 안전망을 증명한다(락 없이도 통과)")
	void 같은_임직원이_10번_동시에_신청하면_1건만_성공() throws Exception {
		Long sessionId = createSession(10);
		Long userId = userIds.get(0);
		List<Callable<EnrollmentResponse>> tasks = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			tasks.add(() -> enrollmentService.enroll(sessionId, userId));
		}

		List<Object> results = runConcurrently(tasks);

		long successes = results.stream().filter(r -> r instanceof EnrollmentResponse).count();
		List<Object> failures = results.stream().filter(r -> r instanceof Throwable).toList();
		assertThat(successes).isEqualTo(1);
		assertThat(failures).hasSize(9)
				.allSatisfy(t -> {
					assertThat(t).isInstanceOf(BusinessException.class);
					assertThat(((BusinessException) t).getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_ENROLLMENT);
				});
		Integer rows = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM enrollment WHERE session_id = ? AND employee_id = ?",
				Integer.class, sessionId, userId);
		assertThat(rows).isEqualTo(1);
		assertThat(confirmedCountOf(sessionId)).isEqualTo(1);
	}

	/**
	 * CANCELLED 행을 같은 행으로 갱신하는 재신청은 새 INSERT가 없어 유일 제약이 막지 못한다.
	 * 따라서 회차 행 락이 유일한 보장이며, 락 없이는 이 테스트가 실패해야 한다.
	 */
	@Test
	@DisplayName("N4: 취소된 임직원이 같은 회차에 10번 동시에 재신청하면 1건만 성공하고 9건은 중복 오류다. 락이 유일한 보장이다")
	void 취소된_임직원이_10번_동시에_재신청하면_1건만_성공() throws Exception {
		Long sessionId = createSession(10);
		Long userId = userIds.get(0);
		CourseSession session = courseSessionRepository.findById(sessionId).orElseThrow();
		User employee = userRepository.findById(userId).orElseThrow();
		enrollmentRepository.saveAndFlush(Enrollment.create(employee, session, EnrollmentStatus.CANCELLED));
		List<Callable<EnrollmentResponse>> tasks = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			tasks.add(() -> enrollmentService.enroll(sessionId, userId));
		}

		List<Object> results = runConcurrently(tasks);

		long successes = results.stream().filter(r -> r instanceof EnrollmentResponse).count();
		List<Object> failures = results.stream().filter(r -> r instanceof Throwable).toList();
		assertThat(successes).isEqualTo(1);
		assertThat(failures).hasSize(9)
				.allSatisfy(t -> {
					assertThat(t).isInstanceOf(BusinessException.class);
					assertThat(((BusinessException) t).getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_ENROLLMENT);
				});
		Integer rows = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM enrollment WHERE session_id = ? AND employee_id = ?",
				Integer.class, sessionId, userId);
		assertThat(rows).isEqualTo(1);
		assertThat(dbCountOf(sessionId, EnrollmentStatus.CONFIRMED)).isEqualTo(1);
		assertThat(confirmedCountOf(sessionId)).isEqualTo(1);
	}

	/**
	 * 락이 없으면 관리자 저장이 오래된 confirmed_count를 덮어써(lost update) 라운드 중 하나 이상에서 실패해야 한다.
	 * 읽기와 flush 사이에 신청 커밋이 끼어야 재현되므로 확률적이다. 여러 라운드로 재현 가능성을 높였다.
	 * 라운드마다 새 회차를 만든다(tearDown이 courseId 기준으로 모두 지운다).
	 */
	@Test
	@DisplayName("N5: 신청 10건과 관리자 회차 수정 50건이 동시에 실행돼도 확정 인원이 덮어써지지 않는다 (여러 라운드)")
	void 신청과_관리자_회차_수정이_동시에_실행돼도_확정_인원이_유지된다() throws Exception {
		for (int round = 1; round <= N5_ROUNDS; round++) {
			Long sessionId = createSession(10);
			List<Callable<EnrollmentResponse>> tasks = new ArrayList<>();
			for (int i = 0; i < 10; i++) {
				Long userId = userIds.get(i);
				tasks.add(() -> enrollmentService.enroll(sessionId, userId));
			}
			for (int i = 0; i < N5_ADMIN_UPDATES; i++) {
				String location = "변경장소-" + i;
				// 장소만 바꾸고 시각·정원은 그대로. 반환값은 쓰지 않으므로 null을 돌려준다.
				tasks.add(() -> {
					courseAdminService.updateSession(sessionId, new SessionRequest(
							LocalDateTime.of(2099, 1, 1, 10, 0), LocalDateTime.of(2099, 1, 1, 18, 0), location, 10));
					return null;
				});
			}

			List<Object> results = runConcurrently(tasks);

			long exceptions = results.stream().filter(r -> r instanceof Throwable).count();
			assertThat(exceptions).as("라운드 %d 예외 수", round).isZero();
			assertThat(dbCountOf(sessionId, EnrollmentStatus.CONFIRMED)).as("라운드 %d CONFIRMED 행 수", round).isEqualTo(10);
			assertThat(confirmedCountOf(sessionId)).as("라운드 %d confirmed_count", round).isEqualTo(10);
		}
	}

	@Test
	@DisplayName("N6: 실제 MySQL의 (임직원, 회차) 유일 제약 위반을 isDuplicateEnrollment가 중복으로 판정한다")
	void 실제_MySQL_유일_제약_위반을_중복으로_판정한다() {
		Long sessionId = createSession(10);
		CourseSession session = courseSessionRepository.findById(sessionId).orElseThrow();
		User employee = userRepository.findById(userIds.get(0)).orElseThrow();
		// 각 saveAndFlush는 리포지토리의 별도 트랜잭션으로 커밋된다. tearDown이 session_id로 지우므로 정리에 영향이 없다.
		enrollmentRepository.saveAndFlush(Enrollment.create(employee, session, EnrollmentStatus.CONFIRMED));

		assertThatThrownBy(() -> enrollmentRepository.saveAndFlush(
				Enrollment.create(employee, session, EnrollmentStatus.CONFIRMED)))
				.isInstanceOfSatisfying(DataIntegrityViolationException.class,
						e -> assertThat(EnrollmentService.isDuplicateEnrollment(e)).isTrue());
	}

	/** 앞 번호 임직원부터 순서대로 신청시켜 (임직원 id -> 신청 응답)을 기록한다. 순번 기대값 계산의 기준이다. */
	private Map<Long, EnrollmentResponse> enrollInOrder(Long sessionId, int count) {
		Map<Long, EnrollmentResponse> applied = new LinkedHashMap<>();
		for (int i = 0; i < count; i++) {
			Long userId = userIds.get(i);
			applied.put(userId, enrollmentService.enroll(sessionId, userId));
		}
		return applied;
	}

	private List<Long> employeeIdsWith(Long sessionId, EnrollmentStatus status) {
		return jdbcTemplate.queryForList(
				"SELECT employee_id FROM enrollment WHERE session_id = ? AND status = ? ORDER BY employee_id",
				Long.class, sessionId, status.name());
	}

	/** 지금 WAITING인 임직원 전원의 내 상태 조회 순번을 오름차순으로 모은다. */
	private List<Integer> currentWaitingNos(Long sessionId) {
		return employeeIdsWith(sessionId, EnrollmentStatus.WAITING).stream()
				.map(userId -> enrollmentService.findMine(sessionId, userId).waitingNo())
				.sorted()
				.toList();
	}

	private List<Long> idsWithStatus(Map<Long, EnrollmentResponse> applied, EnrollmentStatus status) {
		return applied.entrySet().stream()
				.filter(e -> e.getValue().status() == status)
				.map(Map.Entry::getKey)
				.toList();
	}

	private List<Callable<EnrollmentResponse>> cancelTasks(Long sessionId, List<Long> employeeIds) {
		List<Callable<EnrollmentResponse>> tasks = new ArrayList<>();
		for (Long userId : employeeIds) {
			tasks.add(() -> enrollmentService.cancel(sessionId, userId));
		}
		return tasks;
	}

	@Test
	@DisplayName("C1: 정원 10 가득에 대기 20일 때 확정자 10명이 동시에 취소하면 대기 순번 1~10이던 사람이 승급된다")
	void 확정자_10명이_동시에_취소하면_대기_1순위부터_10명이_승급된다() throws Exception {
		Long sessionId = createSession(10);
		Map<Long, EnrollmentResponse> applied = enrollInOrder(sessionId, 30);
		List<Long> confirmedBefore = idsWithStatus(applied, EnrollmentStatus.CONFIRMED);
		// 준비 단계에서 기록한 신청 응답의 순번 1~10인 대기자
		List<Long> firstTenWaiting = applied.entrySet().stream()
				.filter(e -> e.getValue().status() == EnrollmentStatus.WAITING && e.getValue().waitingNo() <= 10)
				.map(Map.Entry::getKey)
				.sorted()
				.toList();
		assertThat(confirmedBefore).hasSize(10);
		assertThat(firstTenWaiting).hasSize(10);

		List<Object> results = runConcurrently(cancelTasks(sessionId, confirmedBefore));

		assertThat(results.stream().filter(r -> r instanceof Throwable).count()).isZero();
		assertThat(dbCountOf(sessionId, EnrollmentStatus.CONFIRMED)).isEqualTo(10);
		assertThat(confirmedCountOf(sessionId)).isEqualTo(10);
		assertThat(employeeIdsWith(sessionId, EnrollmentStatus.CONFIRMED)).containsExactlyElementsOf(firstTenWaiting);
		assertThat(dbCountOf(sessionId, EnrollmentStatus.CANCELLED)).isEqualTo(10);
		assertThat(currentWaitingNos(sessionId)).containsExactlyElementsOf(rangeClosed(1, 10));
	}

	@Test
	@DisplayName("C2: 정원 5 가득에 대기 5일 때 확정자 5명 취소와 새 임직원 10명 신청이 동시에 실행돼도 확정 5이고 대기 순번이 연속이다")
	void 취소와_신규_신청이_동시에_실행돼도_정원과_대기_순번이_유지된다() throws Exception {
		Long sessionId = createSession(5);
		Map<Long, EnrollmentResponse> applied = enrollInOrder(sessionId, 10);
		List<Long> confirmedBefore = idsWithStatus(applied, EnrollmentStatus.CONFIRMED);
		assertThat(confirmedBefore).hasSize(5);
		List<Callable<EnrollmentResponse>> tasks = new ArrayList<>(cancelTasks(sessionId, confirmedBefore));
		for (int i = 10; i < 20; i++) {
			Long userId = userIds.get(i);
			tasks.add(() -> enrollmentService.enroll(sessionId, userId));
		}

		List<Object> results = runConcurrently(tasks);

		assertThat(results.stream().filter(r -> r instanceof Throwable).count()).isZero();
		assertThat(dbCountOf(sessionId, EnrollmentStatus.CONFIRMED)).isEqualTo(5);
		assertThat(confirmedCountOf(sessionId)).isEqualTo(5);
		// 대기 5 + 신규 10 - 승급 5 = 10
		int waiting = dbCountOf(sessionId, EnrollmentStatus.WAITING);
		assertThat(waiting).isEqualTo(10);
		assertThat(currentWaitingNos(sessionId)).containsExactlyElementsOf(rangeClosed(1, waiting));
	}

	@Test
	@DisplayName("C3: 대기자 없이 확정 10명이 동시에 취소하면 확정 인원이 0이다")
	void 대기자_없이_확정_10명이_동시에_취소하면_확정_인원이_0이다() throws Exception {
		Long sessionId = createSession(10);
		enrollInOrder(sessionId, 10);

		List<Object> results = runConcurrently(cancelTasks(sessionId, userIds.subList(0, 10)));

		assertThat(results.stream().filter(r -> r instanceof Throwable).count()).isZero();
		assertThat(confirmedCountOf(sessionId)).isZero();
		assertThat(dbCountOf(sessionId, EnrollmentStatus.CONFIRMED)).isZero();
		assertThat(dbCountOf(sessionId, EnrollmentStatus.CANCELLED)).isEqualTo(10);
	}

	@Test
	@DisplayName("C4: 같은 사람이 같은 신청을 10번 동시에 취소하면 1건만 성공하고 9건은 E005이며 확정 인원은 0이다")
	void 같은_신청을_10번_동시에_취소하면_1건만_성공한다() throws Exception {
		Long sessionId = createSession(10);
		Long userId = userIds.get(0);
		enrollmentService.enroll(sessionId, userId);

		List<Object> results = runConcurrently(cancelTasks(sessionId, Collections.nCopies(10, userId)));

		assertThat(results.stream().filter(r -> r instanceof EnrollmentResponse).count()).isEqualTo(1);
		assertThat(results.stream().filter(r -> r instanceof Throwable).toList()).hasSize(9)
				.allSatisfy(t -> {
					assertThat(t).isInstanceOf(BusinessException.class);
					assertThat(((BusinessException) t).getErrorCode()).isEqualTo(ErrorCode.CANCEL_NOT_ALLOWED);
				});
		assertThat(confirmedCountOf(sessionId)).isZero();
		assertThat(dbCountOf(sessionId, EnrollmentStatus.CANCELLED)).isEqualTo(1);
	}
}
