# 설계명세: 수강 신청과 동시 신청 정원 보장 (FR-04, NFR-01)

- 상태: **승인** (2026-11-16, 9장 Q1~Q3는 제안대로)
- 브랜치: `feature/fr-04-enroll`
- 관련: FR-04, FR-05(자리만), NFR-01, NFR-03, NFR-04 · D-02, D-17, D-21, D-22, D-24, D-27, D-28~D-31(이번에 정함)

## 1. 범위

| 포함 | 제외 (다음 작업) |
|---|---|
| 회차 수강 신청 API, 중복 신청 방지 | 대기 순번 계산·응답 (FR-05) |
| 정원이 차면 `WAITING`으로 저장 | 취소·대기자 자동 승급 (FR-06) |
| 회차 행 비관적 락으로 정원 보장 (NFR-01) | 내 신청 내역 조회 (FR-07) |
| 취소된 신청의 재신청 (D-02의 "한 행 갱신" 규칙) | 잠금 대기 제한·503 응답, 원자적 조건부 UPDATE (4회차 자료 4-3-1 ③④) |
| 동시성 테스트 (Testcontainers MySQL 8.4, D-27) | 프론트 신청 화면 (enrollment-mfe) |

> 취소 API는 FR-06에서 만들지만, 재신청 규칙은 신청 경로의 일부이므로 이번에 구현한다. 테스트는 `CANCELLED` 행을 직접 만들어 확인한다.

## 2. 이번에 정한 사항 (requirements.md에 D-28~D-31로 기록)

| ID | 결정 |
|---|---|
| D-28 | 신청 API는 `POST /api/v1/sessions/{sessionId}/enrollments`. `SecurityConfig`에 이 경로 규칙을 ADMIN 규칙보다 **앞에** 둔다 |
| D-29 | 신청은 `EMPLOYEE` 역할만 한다. ADMIN은 403 |
| D-30 | 신청 기한: 회차가 `OPEN`이고 현재 시각이 `starts_at` **전**일 때만 (D-02의 취소 기한과 같은 기준) |
| D-31 | 정원이 차면 `WAITING`으로 저장하고 응답에 상태만 준다. 대기 순번(`waitingNo`)은 FR-05에서 응답에 추가한다 |

## 3. API

### `POST /api/v1/sessions/{sessionId}/enrollments`

- 인증: `Authorization: Bearer <JWT>`, 역할 `EMPLOYEE` (D-29)
- 신청자: JWT의 `subject`(사용자 id). **요청 본문으로 employeeId를 받지 않는다** (NFR-03). 요청 본문 없음
- 성공: `201 Created`

```json
{
  "enrollmentId": 12,
  "sessionId": 3,
  "status": "CONFIRMED",
  "appliedAt": "2026-11-16T01:23:45.123456Z"
}
```

| 필드 | 설명 |
|---|---|
| `status` | `CONFIRMED`(자리 있음) 또는 `WAITING`(정원 참, D-31) |
| `appliedAt` | 신청(재신청) 시각. 대기 순번의 기준 (FR-05) |

### 오류 응답 (기존 `ErrorResponse` 형식)

| 상황 | HTTP | 코드 | 메시지 | 비고 |
|---|---|---|---|---|
| 토큰 없음·잘못됨 | 401 | G002 | 로그인이 필요합니다. | 기존 |
| ADMIN이 신청 | 403 | G003 | 접근 권한이 없습니다. | 기존, SecurityConfig에서 |
| 회차 없음 | 404 | C002 | 회차를 찾을 수 없습니다. | 기존 |
| 이미 신청함 (`CONFIRMED`·`WAITING`·`COMPLETED` 행 있음) | 409 | **E001** `DUPLICATE_ENROLLMENT` | 이미 신청한 회차입니다. | 신규 |
| 종료된 회차 (`CLOSED`) | 409 | **E002** `SESSION_NOT_OPEN` | 종료된 회차에는 신청할 수 없습니다. | 신규 |
| 이미 시작한 회차 (`now >= starts_at`) | 409 | **E003** `ENROLLMENT_PERIOD_ENDED` | 신청 기간이 지난 회차입니다. | 신규 (D-30) |
| `sessionId`가 숫자가 아님 | 400 | G001 | 입력값이 올바르지 않습니다. | 기존 |

- 기존 `C004 SESSION_CLOSED`는 "변경할 수 없습니다"라는 관리용 메시지라 신청용 E002를 따로 둔다
- 판정 순서: 404 → 409(E002) → 409(E003) → 409(E001). 회차 상태를 먼저 보는 이유: 종료·시작된 회차는 기존 신청 여부와 관계없이 신청할 수 없다

## 4. 도메인·스키마 변경

**스키마 변경 없음.** 필요한 것은 V3에 이미 있다.

| 이미 있는 것 | 쓰임 |
|---|---|
| `uk_enrollment_employee_session (employee_id, session_id)` | 임직원·회차당 한 행 → 중복 방지의 DB 최종 보장 (D-27) |
| `course_session.confirmed_count` + `ck_course_session_seats` (0 ≤ 확정 ≤ 정원) | 남은 자리 판단, 코드가 틀려도 DB가 정원 초과를 막음 (D-21) |
| `idx_enrollment_session_status` | FR-05 대기 순번 계산에서 사용 |

### 엔티티 메서드

| 클래스 | 메서드 | 동작 |
|---|---|---|
| `CourseSession` | `checkEnrollable(LocalDateTime now)` | `CLOSED`면 E002, `now >= startsAt`면 E003 |
| `CourseSession` | `boolean reserveSeat()` | `confirmedCount < capacity`면 1 증가 후 `true`, 아니면 `false`. **락을 잡은 상태에서만 호출** |
| `Enrollment` | `static Enrollment apply(User, CourseSession, boolean confirmed, Instant now)` | 새 신청. `confirmed`면 `CONFIRMED`, 아니면 `WAITING` |
| `Enrollment` | `void reapply(boolean confirmed, Instant now)` | `CANCELLED`일 때만 허용(아니면 E001). 상태와 `appliedAt` 갱신 (D-02) |
| `Enrollment` | `boolean isActive()` 등 | 필요하면 추가 |

- 기존 `Enrollment.create(User, CourseSession, EnrollmentStatus)`는 `SchemaMappingTest`에서만 쓴다. 테스트 픽스처용으로 남긴다
- 시각은 `Clock` 빈을 주입받아 계산한다 (테스트에서 고정). `starts_at`이 `LocalDateTime`(Asia/Seoul)이므로 `LocalDateTime.now(clock)`으로 비교

### Repository

| 인터페이스 | 메서드 |
|---|---|
| `CourseSessionRepository` | `@Lock(PESSIMISTIC_WRITE) Optional<CourseSession> findByIdForUpdate(Long id)` (JPQL `select s from CourseSession s where s.id = :id`) |
| `EnrollmentRepository` | `Optional<Enrollment> findBySessionIdAndEmployeeId(Long sessionId, Long employeeId)` |
| `EnrollmentRepository` | `long countBySessionIdAndStatus(Long sessionId, EnrollmentStatus status)` (테스트·FR-05용) |

## 5. 트랜잭션·동시성 처리

`EnrollmentService.enroll(Long sessionId, Long employeeId)` — `@Transactional` 하나 안에서:

```text
1. session = courseSessionRepository.findByIdForUpdate(sessionId)   -- SELECT ... FOR UPDATE (회차 행 잠금)
             없으면 404 (C002)
2. session.checkEnrollable(LocalDateTime.now(clock))                -- E002, E003
   employee = userRepository.findById(employeeId), 없거나 active=false → 401 (G002, Q1)
3. existing = enrollmentRepository.findBySessionIdAndEmployeeId(...)
   - CONFIRMED / WAITING / COMPLETED → 409 (E001)
4. confirmed = session.reserveSeat()                                 -- 자리 있으면 confirmed_count + 1
5. existing이 CANCELLED → existing.reapply(confirmed, now)
   existing이 없음     → Enrollment.apply(employee, session, confirmed, now)
6. enrollmentRepository.saveAndFlush(...)
   DataIntegrityViolationException → 409 (E001)                      -- 안전망, 롤백으로 +1도 취소됨
7. 커밋 시 잠금 해제
```

| 위험 | 대응 |
|---|---|
| 경쟁 조건 (조회 → 판단 → 저장 사이 끼어들기) | 1에서 회차 행을 잠그므로 같은 회차 신청은 줄을 선다. 2~6은 잠금 안에서 실행 |
| 같은 사람이 동시에 두 번 신청 | 회차 잠금으로 직렬화되어 두 번째는 3에서 E001. 잠금이 빠져도 유일 제약이 막고 6에서 E001 |
| 정원 초과 | 잠금 + `reserveSeat()` + DB 검사 제약(`ck_course_session_seats`) 이중 보장 |
| 교착 상태 | **신청 경로**가 잠그는 행은 회차 하나뿐. enrollment INSERT의 외래 키 검사는 이미 X 잠금을 가진 회차 행이라 추가 대기 없음. 관리자의 과정 종료(`closeCourse`)와 회차 추가(`addSession`)의 교착은 D-32에서 과정 행을 먼저 잠그도록 해결했다 (아래, `docs/design/course-close-lock.md`) |
| 잠금 구간이 길어짐 | 잠금 안에서 외부 호출·알림 없음. 알림(FR-09)은 이후 커밋 후 처리 |
| 잠금 대기 시간 | MySQL 기본 `innodb_lock_wait_timeout`(50초)을 그대로 둔다. 제한·503은 이번 범위 밖 (8회차 성능 점검에서 측정 후 결정) |

- **회차를 바꾸는 모든 경로는 회차 행을 잠근다** (최종 검토 S1 반영). 관리자의 회차 수정·종료, 과정 종료(그 과정의 회차를 범위 조건으로 잠금. InnoDB는 `uk_course_session_no(course_id, session_no)` 인덱스를 훑는 순서로 행과 마지막 행 뒤 간격까지 잠그며, `order by s.id`는 결과 정렬일 뿐 잠금 순서가 아니다)도 `findByIdForUpdate`·`findByCourseIdForUpdate`로 읽는다. 잠그지 않으면 저장할 때 오래된 `confirmed_count`를 덮어써 정원을 넘을 수 있다
- 저장 시 `DataIntegrityViolationException`은 **유일 제약 `uk_enrollment_employee_session` 위반만** E001로 바꾸고, 그 밖의 제약 위반은 로그를 남기고 그대로 던진다(500) (최종 검토 W1 반영)
- MySQL 교착·잠금 대기 초과(`CannotAcquireLockException`)는 현재 500이다. 503 처리는 범위 밖(1장)
- **알려진 교착 (최종 재검토 W-a, D-32에서 해결됨 — 과정 행 선잠금, D-35)**: A `closeCourse`가 회차 행과 뒤 간격을 잠근 상태에서 B `addSession`이 FK 검사로 course 행 S 잠금을 잡고 간격 잠금에 막혀 대기 → A가 flush에서 `UPDATE course`(X 잠금)를 기다리며 교착. (해결 전 현상) InnoDB가 한쪽을 롤백해 그 관리자는 500을 받았다. 데이터는 깨지지 않았다(롤백). FR-04 PR에서는 고치지 않고 D-32 후속 PR에서 해결했다
- **재신청 정합성의 전제**: `enroll`에서 회차 잠금(`findByIdForUpdate`)보다 **앞에 다른 일반 조회를 두지 않는다.** REPEATABLE READ 스냅숏이 잠금 뒤 첫 일반 읽기에서 만들어져야 `findBySessionIdAndEmployeeId`가 앞 트랜잭션의 커밋 결과(CANCELLED → CONFIRMED)를 본다. 어기면 중복 재신청이 생기며, N4가 이 회귀를 잡는다
- `confirmed_count`는 이번에는 **증가만** 한다. 감소(취소)·승급은 FR-06에서 같은 잠금 방식으로
- 신청자 확인: JWT가 유효해도 사용자 행이 없거나 비활성일 수 있다. 처리 방식은 9장 Q1 (제안: 조회해서 401)

## 6. 보안 (NFR-03)

`SecurityConfig.authorizeHttpRequests`에 ADMIN 규칙 앞에 추가:

```java
// FR-04: 회차 수강 신청은 임직원만 (D-28, D-29)
.requestMatchers(HttpMethod.POST, "/api/v1/sessions/*/enrollments").hasRole("EMPLOYEE")
```

- Controller는 `@AuthenticationPrincipal Jwt jwt`에서 `Long.valueOf(jwt.getSubject())`로 신청자를 얻는다
- 순서가 바뀌면 `/api/v1/sessions/**`(ADMIN) 규칙에 먼저 걸려 임직원이 403을 받는다 → 컨트롤러 테스트로 확인

## 7. OpenAPI (NFR-04)

- `EnrollmentController`에 `@Tag(name = "Enrollment")`, `@Operation`, `@ApiResponses`(201, 401, 403, 404, 409)
- 프론트 클라이언트 원본 `frontend/packages/api-client/openapi.json`(D-25) 갱신은 구현계획에서 작업으로 분리

## 8. 테스트 시나리오

### 단위 (`EnrollmentServiceTest`, Mockito)

| # | 시나리오 | 기대 |
|---|---|---|
| U1 | 자리 있음 | `CONFIRMED`, `confirmedCount` +1 |
| U2 | 정원 참 | `WAITING`, `confirmedCount` 그대로 |
| U3 | 회차 없음 | C002 |
| U4 | `CLOSED` 회차 | E002 |
| U5 | `now == startsAt`, `now > startsAt` | E003 (경계값) |
| U6 | 이미 `CONFIRMED`/`WAITING`/`COMPLETED` | E001 |
| U7 | `CANCELLED` 행이 있고 자리 있음 | 같은 행이 `CONFIRMED`, `appliedAt` 갱신 |
| U8 | `CANCELLED` 행이 있고 정원 참 | 같은 행이 `WAITING` |
| U9 | 저장 시 `DataIntegrityViolationException` | E001 |

### 도메인 (`CourseSessionTest`, `EnrollmentTest`)

- `reserveSeat()`: 0 → 1, `capacity - 1` → `capacity`, `capacity`에서 `false`
- `reapply()`: `CANCELLED`가 아니면 E001

### 컨트롤러 (`EnrollmentControllerTest`, MockMvc + H2)

| # | 시나리오 | 기대 |
|---|---|---|
| C1 | EMPLOYEE 토큰으로 신청 | 201, 응답 필드 |
| C2 | 토큰 없음 | 401 G002 |
| C3 | ADMIN 토큰 | 403 G003 |
| C4 | 같은 회차 두 번 | 두 번째 409 E001 |
| C5 | 없는 회차 | 404 C002 |
| C6 | 기존 관리 API(`PUT /api/v1/sessions/{id}`)는 여전히 ADMIN만 | EMPLOYEE 403 (보안 규칙 회귀 확인) |

### 동시성 (`EnrollmentConcurrencyTest`, Testcontainers MySQL 8.4, D-27)

| # | 시나리오 | 기대 |
|---|---|---|
| N1 | 정원 10 회차에 서로 다른 임직원 100명 동시 신청 (`CountDownLatch`로 동시 출발) | 예외 0건, `CONFIRMED` 10, `WAITING` 90, `confirmed_count` 10 |
| N2 | 정원 1 회차에 100명 동시 신청 (NFR-01 문구 그대로 "마지막 1자리") | `CONFIRMED` 1, `WAITING` 99 |
| N3 | 같은 임직원이 같은 회차에 10번 동시 신청 | 성공 1, E001 9, 행 1개 |

- 테스트의 테스트: `findByIdForUpdate`의 `@Lock`을 **개발자가 직접** 주석 처리하면 N1·N2가 실패해야 한다 (4회차 자료 6장 Step 4)
- 테스트 클래스는 `@Transactional`을 쓰지 않는다 (스레드마다 별도 트랜잭션이어야 함). 각 테스트 후 데이터를 지운다
- Docker가 없으면 실패한다 (건너뛰지 않음). 아래 질문 Q2

## 9. 질문과 결정 (2026-11-16 개발자 승인: 모두 제안대로)

| # | 질문 | 결정 |
|---|---|---|
| Q1 | JWT는 유효한데 사용자 행이 없거나 `active = false`이면? | 서비스 3단계 전에 `userRepository.findById`로 확인해 **401 G002**. (`getReferenceById` 대신 조회 1회 추가) |
| Q2 | Docker가 없을 때 동시성 테스트를 건너뛸까 (`disabledWithoutDocker = true`)? | **건너뛰지 않는다.** NFR-01의 증거가 조용히 사라지면 안 된다. 대신 `./gradlew test` 전에 Docker가 필요하다고 CLAUDE.md에 적는다 |
| Q3 | Testcontainers 의존성 추가 (`spring-boot-testcontainers`, `testcontainers-junit-jupiter`, `testcontainers-mysql`, 버전은 Boot BOM) | 추가한다 |
