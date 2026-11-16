# 설계명세: 대기 순번 (FR-05)

- 상태: **완료** (2026-11-16 승인·구현, Q1·Q2 제안대로. 최종 검토 W1·W2 반영)
- 브랜치: `feature/fr-05-waiting-no`
- 관련: FR-05, FR-04, NFR-01, D-02(재신청은 대기 맨 뒤), D-17(순번은 회차 기준), D-31(FR-04는 상태만 응답), D-21

## 1. 범위

| 포함 | 제외 |
|---|---|
| 신청 응답에 대기 순번 `waitingNo` (FR-04 응답 확장, D-31 후속) | 취소·자동 승급 (FR-06) |
| 내 신청 상태 조회 API (현재 대기 순번 확인) — Q1 | 내 신청 목록 (FR-07) |
| 동시 신청 시 순번이 중복·누락 없이 1, 2, 3… (NFR-01) | 관리자 명단 (FR-08) |

## 2. 대기 순번의 정의 (저장하지 않고 계산)

같은 회차의 `WAITING` 신청을 **`appliedAt` 오름차순, 같으면 `id` 오름차순**으로 줄 세운 위치(1부터).

```text
waitingNo = 1 + count(같은 회차, status = WAITING, (appliedAt < 내 appliedAt) 또는 (appliedAt = 내 appliedAt 이고 id < 내 id))
```

- **저장하지 않는 이유**: `max + 1`로 저장하는 순번은 그 자체가 또 다른 경쟁 조건이고(4회차 자료 4-3-1), FR-06에서 앞사람이 취소·승급하면 뒷사람 순번을 모두 고쳐야 한다. 계산하면 취소·승급 뒤 자동으로 당겨진다
- 재신청(D-02)은 `appliedAt`이 갱신되므로 맨 뒤로 간다
- `CONFIRMED`·`CANCELLED`·`COMPLETED`이면 `waitingNo`는 `null`
- 스키마 변경 없음. 기존 인덱스 `idx_enrollment_session_status (session_id, status)`로 회차의 대기 행만 훑는다 (한 회차의 대기자는 많아야 수백 명)

### 2-1. `appliedAt` 동률·역전 (Q2)
- 신청은 회차 행 잠금 안에서 `appliedAt`을 정하므로, 잠금 획득 순서 = `appliedAt` 순서다
- 다만 `Clock.system`은 단조 증가를 보장하지 않는다(NTP 보정으로 뒤로 갈 수 있음). `DATETIME(6)`(마이크로초)라 같은 값이 나올 수도 있다
- 같은 값은 `id`로 결정적으로 정렬된다. **역전**(나중 신청이 더 이른 시각)을 막으려면 잠금 안에서 `appliedAt = max(now, 그 회차 대기자의 최대 appliedAt + 1µs)`로 보정할 수 있다 → Q2

## 3. API

### 3-1. 신청 응답 확장: `POST /api/v1/sessions/{sessionId}/enrollments`

```json
{ "enrollmentId": 12, "sessionId": 3, "status": "WAITING", "appliedAt": "...", "waitingNo": 4 }
```

- `waitingNo`: `WAITING`일 때만 값, 그 밖은 `null` (JSON에 `null`로 포함)
- 신청 트랜잭션 안(회차 잠금을 쥔 상태, 저장 뒤)에서 계산 → 동시 신청에서도 정확

### 3-2. 내 신청 상태 조회 (신규, Q1): `GET /api/v1/sessions/{sessionId}/enrollments/me`

- 인증: `EMPLOYEE` (D-29와 같게). 신청자는 JWT subject
- 200: 3-1과 같은 형식. 대기 중이면 **지금 시점의** 순번 (FR-06 이후 앞사람이 빠지면 줄어든다)
- 404 **E004** `ENROLLMENT_NOT_FOUND` "신청 내역이 없습니다." — 이 회차에 신청한 적 없음
- 404 C002 — 회차 없음
- 읽기 전용 트랜잭션, 잠금 없음 (한 시점 스냅숏이라 계산은 일관됨)
- SecurityConfig: `GET /api/v1/sessions/*/enrollments/me`를 `EMPLOYEE`로, ADMIN 규칙보다 앞에

### 응답 DTO
- `EnrollmentResponse`에 `Integer waitingNo` 추가 (`@Schema`: 대기 중일 때 1부터, 아니면 null)
- `status`의 `allowableValues = {CONFIRMED, WAITING}` 제한을 **푼다** (3-2는 CANCELLED·COMPLETED도 돌려줄 수 있음. FR-04 리뷰 제안)
- `from(Enrollment)` → `of(Enrollment, Integer waitingNo)`로 바꾸고, 서비스가 순번을 계산해 넘긴다

## 4. Repository

```java
/** 같은 회차에서 나보다 앞선 대기자 수 (appliedAt, id 순). waitingNo = 이 값 + 1 */
@Query("""
    select count(e) from Enrollment e
    where e.session.id = :sessionId and e.status = WAITING
      and (e.appliedAt < :appliedAt or (e.appliedAt = :appliedAt and e.id < :id))""")
long countWaitingAhead(sessionId, appliedAt, id);
```

## 5. 트랜잭션·동시성

- **신청**: 기존 `enroll` 흐름 끝(`saveAndFlush` 뒤)에 `WAITING`이면 `countWaitingAhead + 1`. 회차 잠금 안이므로 같은 회차의 다른 신청이 끼어들 수 없다 → 응답 순번이 그 순간 정확하고, 동시 신청 100건의 응답 순번이 1~N으로 중복·누락 없다
- **조회(3-2)**: 잠금 없이 한 트랜잭션에서 내 행 조회 + count. REPEATABLE READ 한 스냅숏이라 계산이 일관된다
- FR-06(취소·승급)이 들어오면 순번은 자동으로 당겨진다 (저장하지 않으므로)
- **전제 (최종 검토 W1)**: `enroll`에서 회차 잠금보다 **앞에 일반 SELECT를 두지 않는다.** MySQL REPEATABLE READ의 스냅숏은 트랜잭션의 첫 일반 읽기에서 만들어지는데, 그 시점이 잠금 뒤여야 `countWaitingAhead`가 앞선 신청의 커밋을 본다. 예를 들어 사용자 조회를 잠금 앞으로 옮기면 정원 판정(잠금 읽기)은 맞아도 대기 순번이 중복된다. 동시성 테스트 N1·N2·N7의 순번 단언이 이 회귀를 잡는다
- **FR-06이 지켜야 할 규칙 (최종 검토 W2)**: 승급 대상(대기 1순위)은 순번과 **같은 정렬 키**로 고른다 — `WAITING`을 `applied_at, id` 오름차순으로 정렬한 첫 행. 취소·승급도 회차 행을 `findByIdForUpdate`로 **먼저 잠근다** (잠금 순서는 D-35와 같다). 그래야 화면의 1순위와 실제 승급자가 같고, 동시 신청의 순번 계산과 경쟁하지 않는다
- 정밀도: `appliedAt`은 DB `DATETIME(6)`에 맞춰 마이크로초로 자른다(POST 응답과 조회 응답 값이 같게)

## 6. 테스트

| 종류 | 시나리오 | 기대 |
|---|---|---|
| 단위 (`EnrollmentServiceTest`) | 자리 있음 | `waitingNo` null |
| | 정원 참 | `countWaitingAhead` 결과 + 1 |
| | 내 상태 조회: 대기 / 확정 / 신청 없음 | 순번 / null / E004 |
| Repository (`EnrollmentRepositoryTest`, H2) | 대기 3명(시각 다름), 같은 시각 2명(id로) | 앞선 수 0, 1, 2 / id 순 |
| 컨트롤러 (`EnrollmentControllerTest`) | 내 상태 조회 200, 신청 없음 404 E004, 관리자 403, 토큰 없음 401 | |
| 동시성 (`EnrollmentConcurrencyTest`, MySQL) | N1(정원 10, 100명) 응답의 `waitingNo` | **1~90이 정확히 한 번씩** (중복·누락 없음) |
| | N2(정원 1, 100명) | 1~99 |
| | 신규 N7: 동시 신청이 끝난 뒤 대기자 각각의 내 상태 조회 | 응답 순번과 같고 1~N |

- 테스트의 테스트 (2026-11-16 개발자 확인)
  - 회차 `@Lock` 제거: N1·N2·N4·N5·N7 실패 (예외 발생이 먼저 드러남)
  - **락은 그대로 두고 사용자 조회를 잠금 앞으로 옮김(W1 전제 위반)**: N1·N2가 순번 단언에서 `[1, 1, …]` 중복으로, N7이 "응답 순번 ≠ 조회 순번"으로, N4가 재신청 10건 모두 성공(오래된 CANCELLED를 봄)으로 실패. 정원·예외 단언은 통과 → 순번 단언이 스냅숏 전제를 실제로 잡는다

## 7. 질문과 결정 (2026-11-16 개발자 승인: 모두 제안대로)

| # | 질문 | 결정 |
|---|---|---|
| Q1 | 내 신청 상태 조회 API(3-2)를 이번에 만들까? "대기 순번을 알려 준다"를 신청 순간뿐 아니라 이후에도 확인하려면 필요하다. FR-07(내 신청 목록)은 따로 | **만든다** |
| Q2 | `appliedAt` 역전(시계가 뒤로 감)을 보정할까? | **보정하지 않는다.** 동률은 id로 정렬, 역전은 드물고 영향이 순번 한 칸이라 기록만(D-36) 남긴다. 필요해지면 잠금 안 보정 추가 |

## 8. 결정 사항 기록 (승인 시)
- D-36: 대기 순번은 저장하지 않고 (appliedAt, id) 순으로 계산, Q1·Q2 결과
- D-31 상태를 **완료**로
