# 설계명세: 내 신청 내역 조회 (FR-07)

- 상태: **완료** (2026-11-26 승인·구현, Q1~Q6 제안대로. 결정 D-43. JPQL 그대로 사용(네이티브 전환 없음). SQL 1개는 3건·6건 측정으로 확인. EXPLAIN에서 filesort가 사라져 V5 인덱스 추가)
- 구현계획: `docs/plans/fr-07.md`
- 브랜치: `feature/fr-07-my-enrollments` (**`feature/fr-09-notification` 위에서 시작**. PR #18이 V4를 쓰므로 이 작업의 마이그레이션은 V5. #18 머지 뒤 main으로 rebase)
- 관련: FR-07, NFR-02(N+1 없음), NFR-03(본인 것만), NFR-04(OpenAPI), D-22·D-24(경로·인증), D-29(신청은 EMPLOYEE), D-36(대기 순번 정의), D-38(목록 `status` 파라미터 처리)

## 1. 범위

| 포함 | 제외 |
|---|---|
| 로그인한 임직원의 신청 내역 목록(상태 필터, 최신 신청순) | 화면(`enrollment-mfe`, 6장) |
| 대기 신청의 현재 대기 순번(계산) | FR-08(관리자 명단·수료 처리) |
| 인증 정보에서 신청자 ID를 꺼내는 `CurrentUser` | 알림 목록(FR-09 후속) |

## 2. 결정 (2026-11-26 개발자 선택, 계획 단계 Q1~Q4 제안대로)

| # | 내용 |
|---|---|
| Q1 | 경로는 D-24대로 **`GET /api/v1/me/enrollments`**. `EMPLOYEE`만(관리자 403, D-29와 같은 기준) |
| Q2 | **페이지 없이 전체 목록**. 목록 API 규칙(`Pageable` + `PageResponse`)을 따르면 목록 + count로 SQL이 2개가 되어 "SQL 1개" 요구와 충돌한다. 한 사람의 신청 수는 과정 회차 수를 넘지 않을 만큼 작다. 규칙의 예외로 D-43에 기록 |
| Q3 | `global.security.CurrentUser` record를 새로 두고 `CurrentUser.from(jwt).employeeId()`로 신청자 ID를 꺼낸다. 기존 `EnrollmentController`의 `Long.valueOf(jwt.getSubject())` 세 곳도 이것으로 바꾼다(동작 동일). 신청자 ID를 요청 파라미터로 받지 않는다(NFR-03) |
| Q4 | PR #18(FR-09, V4) 위에서 작업하고 인덱스는 V5 |

## 3. API

### `GET /api/v1/me/enrollments`
- 인증: Bearer JWT, `ROLE_EMPLOYEE`. 신청자 = JWT subject
- 쿼리 파라미터: `status` (선택, `CONFIRMED`·`WAITING`·`CANCELLED`·`COMPLETED`). 생략하면 전체
- 정렬: `applied_at` 내림차순, 같으면 `id` 내림차순 (최근 신청이 위). 재신청한 행은 `applied_at`이 갱신되므로 위로 올라온다(D-02)
- 응답 200: `MyEnrollmentResponse` 배열 (빈 배열 가능)

| 필드 | 타입 | 설명 |
|---|---|---|
| `enrollmentId` | long | 신청 ID |
| `sessionId` | long | 회차 ID |
| `courseTitle` | string | 과정 제목 |
| `sessionStartsAt` | date-time (LocalDateTime) | 회차 시작 일시 |
| `location` | string | 회차 장소 |
| `status` | enum | 신청 상태 |
| `waitingNo` | integer, nullable | `WAITING`일 때만 현재 대기 순번(1부터), 그 밖은 `null` |
| `appliedAt` | date-time (Instant) | 신청 일시 (재신청이면 마지막 신청 시각) |

| 상태 | 코드 | 경우 |
|---|---|---|
| 400 | G001 | `status` 값이 enum에 없음 (기존 `MethodArgumentTypeMismatchException` 처리, D-38과 같음) |
| 401 | G002 | 토큰 없음·만료 |
| 403 | G003 | 관리자 토큰 |

- OpenAPI: `@Operation(summary = "내 신청 내역", description = …)`, `@ApiResponses` 200·400·401·403(오류는 `ErrorResponse`), `status` 파라미터 설명

## 4. 조회 방식: SQL 1개

`EnrollmentRepository`에 JPQL 한 문장을 둔다. 과정·회차는 조인, 대기 순번은 상관 서브쿼리, 결과는 엔티티가 아니라 프로젝션 record로 바로 만든다(지연 로딩이 생길 여지가 없음).

```sql
select new com.eduhub.enrollment.repository.MyEnrollmentRow(
         e.id, s.id, c.title, s.startsAt, s.location, e.status, e.appliedAt,
         case when e.status = WAITING then
           (select count(w) + 1 from Enrollment w
             where w.session = s and w.status = WAITING
               and (w.appliedAt < e.appliedAt or (w.appliedAt = e.appliedAt and w.id < e.id)))
         end)
from Enrollment e join e.session s join s.course c
where e.employee.id = :employeeId
  and (:status is null or e.status = :status)
order by e.appliedAt desc, e.id desc
```

- **대기 순번**은 저장하지 않고 계산한다(D-36). 비교식은 기존 `countWaitingAhead`와 **같다**: 같은 회차 `WAITING` 중 `applied_at`이 앞서거나, 같으면 `id`가 작은 행 수 + 1. 그래서 회차별 내 상태 조회(`GET …/sessions/{id}/enrollments/me`)의 `waitingNo`와 항상 같다
- 프로젝션 `MyEnrollmentRow(Long enrollmentId, Long sessionId, String courseTitle, LocalDateTime sessionStartsAt, String location, EnrollmentStatus status, Instant appliedAt, Long waitingNo)` → 서비스에서 `MyEnrollmentResponse`(waitingNo는 Integer)로 변환
- 읽기 전용 트랜잭션, 잠금 없음. 다른 요청이 동시에 신청·취소해도 MySQL REPEATABLE READ의 한 스냅숏에서 목록과 순번을 함께 읽으므로 한 응답 안에서는 일관된다
- 위험: Hibernate가 생성자 식 안의 `case` + 서브쿼리를 지원하지 않거나 타입 추론이 다르면 구현 첫 단계(Repository 테스트)에서 드러난다. 그때는 같은 SQL을 네이티브 쿼리 + 인터페이스 프로젝션으로 바꾸고 보고한다(SQL 1개 조건은 유지)

## 5. 인덱스 (V5, 확인 뒤 추가)

| 쿼리 부분 | 기존 인덱스 | 판단 |
|---|---|---|
| `employee_id`로 거르기 | `uk_enrollment_employee_session (employee_id, session_id)` 선두 | 거르기는 된다. `applied_at` 정렬은 별도 정렬 필요 |
| 최신순 정렬 | 없음 | **제안**: `idx_enrollment_employee_applied (employee_id, applied_at)` → 정렬 없이 인덱스 순서로 읽음 |
| 대기 순번 서브쿼리 | `idx_enrollment_session_status (session_id, status)` | 사용 가능(나머지 조건은 행에서 비교). 변경 없음 |

- 구현 중 dev MySQL에서 `EXPLAIN`(읽기 전용 MCP `eduhub-db`)으로 전후를 비교한다. 정렬 단계(`Using filesort`)가 사라지지 않거나 효과가 없으면 V5를 넣지 않고 그 결과를 보고한다
- 넣는다면 `V5__add_enrollment_employee_applied_index.sql` 한 문장. MySQL·H2 양쪽 실행. 엔티티 `@Table(indexes=…)`에도 같은 이름으로 추가. `validate`는 인덱스를 확인하지 않으므로 SchemaMappingTest가 `INFORMATION_SCHEMA`로 인덱스 존재와 컬럼 순서(employee_id, applied_at)를 직접 확인

## 6. 코드 구조

| 위치 | 내용 |
|---|---|
| `global/security/CurrentUser` (신규) | `public record CurrentUser(Long employeeId) { public static CurrentUser from(Jwt jwt) }` — subject를 Long으로. Javadoc: 신청자 ID는 반드시 여기서 꺼낸다(NFR-03) |
| `enrollment/repository/MyEnrollmentRow` (신규) | 4장 프로젝션 record |
| `enrollment/repository/EnrollmentRepository` | `List<MyEnrollmentRow> findMyEnrollments(@Param("employeeId") Long employeeId, @Param("status") EnrollmentStatus status)` (4장 JPQL) |
| `enrollment/dto/MyEnrollmentResponse` (신규) | 3장 필드, `@Schema`(waitingNo nullable 설명), `static from(MyEnrollmentRow)` |
| `enrollment/service/MyEnrollmentQueryService` (신규) | `@Service @Transactional(readOnly = true)`, `List<MyEnrollmentResponse> findMine(Long employeeId, EnrollmentStatus status)` — 리포지토리 1회 호출 후 변환. 기존 `EnrollmentService`(신청·취소)와 조회를 나눈다(`CourseService`/`CourseAdminService`와 같은 방식) |
| `enrollment/controller/EnrollmentController` | `@GetMapping("/me/enrollments") List<MyEnrollmentResponse> myEnrollments(@RequestParam(required = false) EnrollmentStatus status, @AuthenticationPrincipal Jwt jwt)`, 기존 세 메서드는 `CurrentUser.from(jwt).employeeId()`로 |
| `global/config/SecurityConfig` | `.requestMatchers(HttpMethod.GET, "/api/v1/me/enrollments").hasRole("EMPLOYEE")` (주석 `// FR-07: 내 신청 내역은 임직원만 (D-43)`) — 기존 FR-04~06 규칙 옆 |
| `frontend/packages/api-client/openapi.json` | 백엔드를 띄워 `refresh:spec` → `generate:api` (D-25) |

- 계층: controller → service → repository. `enrollment` 안에서 끝나며 `course` 도메인은 JPQL 조인으로만 읽는다

## 7. 테스트

### Repository (`@DataJpaTest`, H2) — `EnrollmentRepositoryTest`에 추가
- R1 내 것만: 임직원 A·B가 같은 회차·다른 회차에 신청 → A 조회에 A 행만
- R2 정렬: `applied_at` 내림차순, 동률이면 `id` 내림차순
- R3 상태 필터: `WAITING`이면 대기만, `null`이면 전체
- R4 대기 순번: 같은 회차 대기 3명 중 2번째 → 2, 동률 시각이면 id 순, 확정·취소 행은 `null`. 같은 행에 대해 `countWaitingAhead + 1`과 같은지 교차 확인
- R5 필드: 과정 제목, 회차 시작 일시·장소가 조인 값과 일치

### SQL 1개 (`@SpringBootTest`, H2, `spring.jpa.properties.hibernate.generate_statistics=true`)
- 신규 `enrollment/MyEnrollmentQueryCountTest`: 서로 다른 회차·과정 3건(확정·대기·취소) 신청을 준비 → `Statistics.clear()` → `myEnrollmentQueryService.findMine(…)` → `getPrepareStatementCount() == 1`, 결과 3건이고 대기 행의 순번이 채워짐. 행이 늘어도(예: 6건) 1인지 함께 확인(N+1 회귀 방지)

### 컨트롤러 (`EnrollmentControllerTest`, 기존 방식: local 초기 데이터 + 실제 로그인 토큰 + `@Transactional`)
- C1 신청 후 `GET /api/v1/me/enrollments` → 200, 내 신청 1건, 필드 값
- C2 `?status=WAITING` 필터, `?status=NOPE` → 400 G001
- C3 관리자 토큰 403 G003, 토큰 없음 401 G002
- C4 다른 임직원(테스트에서 만든 계정으로 로그인)의 신청은 보이지 않음
- 회귀: 기존 신청·취소·내 상태 조회 테스트(`CurrentUser` 교체 뒤에도 통과)

### 단위
- `CurrentUser.from`: subject "42" → 42
- `MyEnrollmentQueryService`: 리포지토리 1회 호출, 변환(waitingNo Long → Integer, null 유지)

### 전체
- `./gradlew test` (Docker 동시성 포함), `spotlessCheck`

## 8. 질문

| # | 질문 | 제안 |
|---|---|---|
| Q5 | 취소(CANCELLED) 신청도 기본 목록에 보일까? | **보임** (FR-07 요구에 "확정, 대기, 취소, 수료"가 모두 있음). 필터로 숨김 |
| Q6 | 목록 행 수 상한 | **두지 않음** (Q2와 같은 이유). 실제로 커지면 Slice 페이지로 바꾸고 새 결정으로 기록 |

## 9. 결정 사항 기록 (승인 시)
- D-43: FR-07 내 신청 내역 API(경로, 권한, 페이지 없음의 이유와 규칙 예외, SQL 1개 조회 방식, 대기 순번 계산 규칙 공유, `CurrentUser`), 인덱스 결과(V5 추가 여부)
