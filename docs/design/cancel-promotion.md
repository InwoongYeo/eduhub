# 설계명세: 신청 취소와 대기자 자동 승급 (FR-06)

- 상태: **완료** (2026-11-16 승인·구현, Q1~Q4 제안대로. 최종 검토 W1 반영)
- 브랜치: `feature/fr-06-cancel`
- 관련: FR-06, NFR-01, NFR-03, D-02(취소 기한·재신청), D-21(확정 인원), D-35(잠금 순서), D-36(대기 순번 정의와 FR-06 규칙)

## 1. 범위

| 포함 | 제외 |
|---|---|
| 본인 신청 취소 (확정·대기 모두) | 관리자의 대리 취소 |
| 확정자가 취소하면 대기 1순위 자동 확정 | 승급 알림 (FR-09) |
| 대기자가 없으면 확정 인원 감소 | 정원을 늘렸을 때 자동 승급 (Q3) |
| 동시 취소·신청에서 정원·순번 정합성 (NFR-01) | 내 신청 목록 (FR-07) |

## 2. 규칙

| 내 상태 | 취소 결과 | 확정 인원(`confirmed_count`) | 대기열 |
|---|---|---|---|
| `CONFIRMED` | `CANCELLED` | 대기자가 있으면 그대로(1순위가 CONFIRMED로), 없으면 −1 | 1순위가 빠지고 나머지가 한 칸씩 당겨짐(순번은 계산값이라 자동, D-36) |
| `WAITING` | `CANCELLED` | 그대로 | 뒷사람이 한 칸씩 당겨짐 |
| `CANCELLED`·`COMPLETED` | **409 E005** 취소할 수 없는 신청 | — | — |

- **기한 (D-02)**: 회차 시작 시각(`starts_at`) **전**까지만 취소할 수 있다. 시작 뒤에는 **409 E006**. 승급은 취소 안에서만 일어나므로 승급도 자연히 기한 안에서만 일어난다
- **종료된 회차(CLOSED)**: 취소할 수 없다 → **409 E006** (Q2)
- **승급 대상 (D-36 규칙)**: 같은 회차 `WAITING`을 `applied_at, id` 오름차순으로 정렬한 **첫 행** = 화면의 대기 1순위
- 승급된 신청의 `applied_at`은 바꾸지 않는다 (신청 시각 기록 유지)
- 재신청(D-02)은 FR-04 경로 그대로: 취소된 행을 갱신, 자리가 있으면 확정·없으면 대기 맨 뒤

## 3. API

### `POST /api/v1/sessions/{sessionId}/enrollments/me/cancel` (Q1)

- 인증: `EMPLOYEE` (D-29와 같게). 대상은 JWT subject의 신청만 (NFR-03). 요청 본문 없음
- 200: 취소된 내 신청 (`EnrollmentResponse`, `status` = `CANCELLED`, `waitingNo` = null) (Q4)
- 오류

| 상황 | HTTP | 코드 |
|---|---|---|
| 토큰 없음 / 관리자 | 401 G002 / 403 G003 | 기존 |
| 회차 없음 | 404 | C002 |
| 이 회차에 신청 없음 | 404 | E004 (기존) |
| 이미 취소됨·수료됨 | 409 | **E005** `CANCEL_NOT_ALLOWED` "취소할 수 없는 신청입니다." |
| 회차가 종료됐거나 이미 시작함 | 409 | **E006** `CANCEL_PERIOD_ENDED` "취소 기간이 지난 회차입니다." |

- 판정 순서: 404(C002) → 404(E004) → 409(E006) → 409(E005)
- SecurityConfig: `POST /api/v1/sessions/*/enrollments/me/cancel`를 `EMPLOYEE`로, ADMIN 규칙보다 앞에

## 4. 도메인·Repository

| 클래스 | 추가 |
|---|---|
| `Enrollment` | `void cancel()`: `CONFIRMED`·`WAITING`만 `CANCELLED`로, 그 밖은 E005. `boolean isConfirmed()` · `void promote()`: `WAITING`만 `CONFIRMED`로 (그 밖은 `IllegalStateException`, 서비스 버그) |
| `CourseSession` | `void checkCancellable(LocalDateTime now)`: CLOSED 또는 `now >= startsAt`이면 E006. `void releaseSeat()`: `confirmedCount > 0`이면 −1 (0이면 `IllegalStateException`). 회차 잠금 안에서만 호출 |
| `EnrollmentRepository` | `Optional<Enrollment> findFirstBySessionIdAndStatusOrderByAppliedAtAscIdAsc(Long sessionId, EnrollmentStatus status)` — 승급 대상 (D-36 정렬) |

- 스키마 변경 없음. `ck_course_session_seats`(0 ≤ 확정 ≤ 정원)가 감소도 지켜 준다

## 5. 트랜잭션·동시성

`EnrollmentService.cancel(Long sessionId, Long employeeId)` — `@Transactional` 하나:

```text
1. session = findByIdForUpdate(sessionId)      -- 회차 행 잠금 먼저 (D-36 W2, 잠금 순서 D-35)
                                                   이 앞에 일반 SELECT를 두지 않는다 (스냅숏 전제, D-36)
   없으면 C002
2. mine = findBySessionIdAndEmployeeId(...)      없으면 E004
3. session.checkCancellable(now)                 E006
4. wasConfirmed = mine.isConfirmed(); mine.cancel()   E005
5. wasConfirmed이면
     next = findFirst...WAITING (applied_at, id)
     있으면 next.promote()                       -- confirmed_count 그대로
     없으면 session.releaseSeat()                -- confirmed_count −1
6. saveAndFlush, 응답
```

- **같은 회차의 신청·취소·승급은 모두 회차 행 잠금으로 줄을 선다.** 승급 대상 조회는 잠금 안의 일반 SELECT지만, 같은 회차의 `enrollment`를 바꾸는 경로가 모두 이 잠금을 먼저 잡으므로 다른 트랜잭션이 끼어들지 않는다. 그래서 `FOR UPDATE SKIP LOCKED`가 필요 없다 (4회차 자료 4-3-1의 SKIP LOCKED는 회차 잠금이 없는 원자적 UPDATE 방식용)
- 교착: 잠그는 행은 회차 하나. `enrollment` 행 UPDATE는 회차 잠금을 가진 트랜잭션만 하므로 대기 없음
- 관리자의 회차 종료(`closeSession`·`closeCourse`)도 회차 행을 잠그므로 취소와 직렬화된다 (D-32/D-35)

## 6. 테스트

### 단위·도메인
- `Enrollment.cancel/promote`, `CourseSession.checkCancellable/releaseSeat` (경계: `now == startsAt` → E006, `confirmedCount == 0`에서 release → 예외)
- `EnrollmentServiceTest`: 확정 취소 + 대기 있음 → 1순위 승급·확정 인원 그대로 / 확정 취소 + 대기 없음 → −1 / 대기 취소 → 변화 없음 / E004·E005·E006·C002

### Repository (H2)
- `findFirst...`: 대기 3명(시각 다름) → 가장 이른 사람, 동률 → id 작은 쪽, 재신청 행(작은 id·늦은 시각) → 뒤로

### 컨트롤러
- 취소 200(CANCELLED), 다시 취소 409 E005, 신청 없음 404 E004, 관리자 403, 토큰 없음 401
- 취소 후 재신청 → 201 (D-02)

### 동시성 (`EnrollmentConcurrencyTest`, MySQL)
| # | 시나리오 | 기대 |
|---|---|---|
| C1 | 정원 10 가득 + 대기 20. 확정자 10명이 동시에 취소 | 예외 0. 확정 행 10 = `confirmed_count` 10. **승급된 사람 = 대기 순번 1~10이던 사람**. 남은 대기 순번 1~10 |
| C2 | 정원 5 가득 + 대기 5. 확정자 5명 취소 + 새 임직원 10명 신청 동시 | 예외 0. 확정 행 수 = `confirmed_count` ≤ 정원, 대기 순번 1~N 중복·누락 없음 |
| C3 | 대기자 없이 확정 10명 동시 취소 | `confirmed_count` 0, 확정 행 0 |
| C4 | 같은 사람이 같은 신청을 10번 동시에 취소 | 성공 1, E005 9 |

- 테스트의 테스트 (2026-11-16 개발자 확인): 회차 `@Lock`을 빼면 C1 "expected 10 but 1"(승급 누락), C2 "expected 5 but 2", C3 "expected 0 but 9"(확정 인원 lost update), C4 "expected 1 but 10"(중복 취소)으로 **C1~C4 모두 실패**. 복원 후 통과
- 승급 대상 정렬: C1은 순서대로 신청해 `applied_at` 순서와 `id` 순서가 같으므로, 정렬을 **내림차순**으로 바꿀 때만 C1이 실패한다. `applied_at`과 `id`가 어긋나는 재신청 행의 정렬은 Repository 테스트(H2)가 잡는다 (최종 검토 S1)
- 본인 신청만 취소(NFR-03): 다른 임직원 토큰으로 취소하면 404 E004이고 원래 신청은 그대로임을 컨트롤러 테스트로 확인 (최종 검토 W1)

## 7. 질문과 결정 (2026-11-16 개발자 승인: 모두 제안대로)

| # | 질문 | 결정 |
|---|---|---|
| Q1 | 취소 API 형태: `POST …/enrollments/me/cancel` vs `DELETE …/enrollments/me` | **POST …/me/cancel**. 행을 지우지 않고 상태를 바꾸며, 과정·회차의 `…/close`와 같은 모양 |
| Q2 | 관리자가 종료(CLOSED)한 회차의 신청을 취소할 수 있나? | **불가 (409 E006).** 종료된 회차는 바꾸지 않는다(D-03과 같은 취지) |
| Q3 | 관리자가 정원을 늘리면 대기자를 자동 승급할까? | **이번 범위 밖.** 결정(D-37)에 후속 과제로 기록. 지금은 정원을 늘려도 대기자는 그대로이고, 새 신청자가 빈자리를 먼저 차지할 수 있다 |
| Q4 | 취소 응답 | **취소된 내 신청(200)**. 누가 승급됐는지는 돌려주지 않는다(다른 사람 정보) |

## 8. 결정 사항 기록 (승인 시)
- D-37: 취소 API·규칙·오류 코드, Q2·Q3·Q4 결과. D-02 상태를 **완료**로
