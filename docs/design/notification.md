# 설계명세: 신청 확정·승급 알림 기록 (FR-09)

- 상태: **완료** (2026-11-26 승인·구현, Q1~Q4 제안대로. 결정 D-42. 롤백 테스트 N2는 트랜잭션 안에서 알림 1건을 먼저 확인하도록 보강했고, `record`를 `REQUIRES_NEW`로 바꾸면 N2만 실패함을 확인)
- 브랜치: `feature/fr-09-notification`
- 구현계획: `docs/plans/fr-09.md`
- 관련: FR-09, FR-04(신청), FR-06(취소·승급), NFR-01(잠금 안의 작업), NFR-03(로그에 개인정보 없음), D-20(Flyway), D-36(스냅숏 전제), D-41(정원 증가 승급, 구현 전)

## 1. 범위

| 포함 | 제외 |
|---|---|
| 신청이 확정될 때, 대기자가 승급될 때 알림 1건을 DB(`notification`)에 남기고 INFO 로그 | 메일·메신저 등 실제 발송 |
| Flyway로 `notification` 테이블 추가 | 프론트 화면, 알림 조회 API (FR-07 등 후속) |
| | 대기 등록·취소·수료 알림 |

## 2. 방식: 같은 트랜잭션에서 바로 저장 (B안, 2026-11-26 개발자 선택)

`EnrollmentService`가 확정·승급하는 지점에서 `NotificationService.record(...)`를 직접 호출한다. 이벤트(`AFTER_COMMIT` + `REQUIRES_NEW`)는 쓰지 않는다.

| | 이벤트 + 커밋 뒤 저장 (A, 채택 안 함) | **같은 트랜잭션 저장 (B, 채택)** |
|---|---|---|
| 신청이 롤백되면 알림 없음 | 커밋 뒤에만 실행 | 함께 롤백 |
| 확정됐는데 알림 기록이 없는 경우 | 커밋 뒤 저장 실패·서버 종료 시 생김 | **없음** (원자적) |
| 커밋 뒤 리스너 예외 | 신청은 성공했는데 응답 500이 될 수 있어 별도 처리 필요 | 해당 없음 |
| 회차 잠금 유지 시간 | 영향 없음 | INSERT 1건만큼 늘어남(신청도 INSERT를 하므로 미미) |
| 알림 저장 실패 시 | 신청 유지 | 신청도 롤백 (같은 DB INSERT 실패는 어차피 장애) |

**이유**: FR-09의 1단계는 "로그·DB 기록"이므로 "확정된 신청에는 반드시 기록이 있다"는 정합성이 가장 중요하다. 외부 발송이 생기면 이 테이블을 **보낼 목록(outbox)**으로 쓰고, 발송은 커밋된 행을 읽는 별도 작업이 맡는다(그때 다시 설계). 그래서 지금 이벤트를 넣을 이유가 없다.

## 3. 규칙

| 시점 | 대상 | `type` | 메시지 예 |
|---|---|---|---|
| 신청(`enroll`) 결과가 `CONFIRMED` (재신청 포함, Q2) | 신청자 | `ENROLLMENT_CONFIRMED` | `'자바 기초' 1회차 수강 신청이 확정되었습니다.` |
| 신청 결과가 `WAITING` | — | 기록 안 함 | — |
| 확정자 취소(`cancel`)로 대기 1순위가 `CONFIRMED` | **승급된 사람** (취소한 사람 아님) | `WAITLIST_PROMOTED` | `'자바 기초' 1회차 대기 중이던 신청이 확정되었습니다.` |
| 대기자 취소, 대기자 없는 확정자 취소 | — | 기록 안 함 | — |
| 정원 증가 승급 (D-41, 구현 전) | 승급된 사람마다 | `WAITLIST_PROMOTED` | 위와 같음. **늦게 머지되는 쪽이 연결한다** (Q3) |

- 메시지는 서버가 만든 고정 문구 + 과정 제목(최대 100자) + 회차 번호. 255자를 넘지 않는다
- 시각은 주입된 `Clock`, `DATETIME(6)`에 맞춰 마이크로초로 자른다 (신청의 `applied_at`과 같은 방식)
- 같은 사람이 취소 후 재신청해 다시 확정되면 알림이 한 건 더 생긴다 (기록이므로 중복 제거 안 함)

## 4. 스키마 (`V4__create_notification.sql`)

```sql
CREATE TABLE notification (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    employee_id BIGINT       NOT NULL,
    type        VARCHAR(30)  NOT NULL,
    message     VARCHAR(255) NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_employee FOREIGN KEY (employee_id) REFERENCES users (id)
);

CREATE INDEX idx_notification_employee_created ON notification (employee_id, created_at);
```

- 기존 V1~V3는 고치지 않는다 (D-20, protect-files 훅). MySQL 8.4와 H2(MySQL 모드) 양쪽에서 실행
- 컬럼은 요청대로 최소. 신청·회차 참조(`enrollment_id` 등)는 넣지 않는다 (Q1)
- 인덱스는 FR-07(내 내역)에서 "내 알림 최신순" 조회를 대비

## 5. 코드 구조

새 패키지 `com.eduhub.notification` (계층 규칙대로 `domain`/`repository`/`service`, 컨트롤러·DTO 없음)

| 위치 | 내용 |
|---|---|
| `notification/domain/NotificationType` | enum `ENROLLMENT_CONFIRMED`, `WAITLIST_PROMOTED` |
| `notification/domain/Notification` | 엔티티. `@ManyToOne(fetch = LAZY, optional = false) User employee`, `@Enumerated(STRING) type`, `message`, `Instant createdAt`. `protected` 기본 생성자, 정적 팩토리 `create(...)`, setter 없음 |
| `notification/repository/NotificationRepository` | `JpaRepository<Notification, Long>` (테스트용 `countByEmployeeIdAndType` 정도) |
| `notification/service/NotificationService` | `@Transactional(propagation = MANDATORY) void record(User employee, CourseSession session, NotificationType type)`: 메시지 생성 → 저장 → `log.info("notification type={} employeeId={} sessionId={}", …)`. **로그에 이메일·이름 등 개인정보를 넣지 않는다** (NFR-03). `MANDATORY`: 호출자 트랜잭션 안에서만 기록되게 강제 |
| `enrollment/service/EnrollmentService` | 생성자에 `NotificationService` 추가. `enroll`: `saveAndFlush` 성공 뒤 `CONFIRMED`면 `record(employee, session, ENROLLMENT_CONFIRMED)`. `cancel`: `next.get().promote()` 뒤 `record(next.get().getEmployee(), session, WAITLIST_PROMOTED)` |

- 의존 방향: `enrollment` → `notification` → (`user`, `course`의 도메인 읽기). `notification`은 `enrollment`를 참조하지 않는다 (순환 없음)

## 6. 트랜잭션·동시성

- `record`는 신청·취소 트랜잭션에 합류한다. 회차 행 잠금을 쥔 상태에서 실행되므로 잠금 순서·교착 영향 없음 (잠그는 행은 그대로 회차 하나)
- **D-36 스냅숏 전제 유지**: `record`는 잠금 **뒤**에만 호출되고, 하는 일은 과정 제목 지연 로딩(SELECT)과 INSERT뿐이다. 잠금 앞에 새 조회를 만들지 않는다
- 동시 신청 100명(N1 등)에서도 알림은 확정된 사람 수만큼만 생긴다 (대기는 기록 안 함)
- **테스트 정리 영향**: `notification.employee_id`가 `users`를 참조하므로, 사용자를 지우는 테스트 정리(`EnrollmentConcurrencyTest.tearDown`의 `DELETE FROM users …`)는 그 전에 해당 사용자의 `notification`을 지워야 한다. 안 그러면 FK 위반으로 정리가 실패한다

## 7. 테스트

### 단위 (Mockito)
- `NotificationServiceTest`: 두 type의 메시지 문구(과정 제목·회차 번호 포함), `createdAt` = `Clock` 시각(마이크로초), 저장 1회
- `EnrollmentServiceTest`:
  - 신청 `CONFIRMED` → `record(…, ENROLLMENT_CONFIRMED)` 1회 / `WAITING` → 호출 없음
  - 재신청으로 `CONFIRMED` → 1회
  - 확정자 취소 + 대기 있음 → 승급자에게 `WAITLIST_PROMOTED` 1회, 취소자에게는 없음
  - 확정자 취소 + 대기 없음, 대기자 취소 → 호출 없음
  - 거절(E001 중복, E002·E003 기한) → 호출 없음

### 통합 (`@SpringBootTest`, H2, 테스트 클래스에 `@Transactional` 없음 — 실제 커밋·롤백을 보기 위해)
- **N1 신청 확정 시 알림 1건**: 새 임직원·회차로 신청 → 커밋 → `notification` 1건, type `ENROLLMENT_CONFIRMED`, 메시지에 과정 제목
- **N2 롤백되면 알림 없음**: `TransactionTemplate` 안에서 `enrollmentService.enroll(...)` 성공 후 `setRollbackOnly()` → 신청 0건, 알림 0건
- N3 대기 등록 시 알림 0건
- N4 확정자 취소 → 승급자에게 `WAITLIST_PROMOTED` 1건
- 각 테스트는 만든 행(알림 → 신청 → 회차 → 과정 → 사용자 순)을 지운다
- `SchemaMappingTest`가 V4와 `Notification` 엔티티 일치를 `validate`로 확인 (기존 테스트, 추가 작업 없음)

### 회귀
- `EnrollmentConcurrencyTest`(Docker): `tearDown`에 알림 삭제를 추가한 뒤 N1~N7·C1~C4 통과
- 전체 `./gradlew test` 통과

## 8. 질문과 결정

| # | 질문 | 제안 |
|---|---|---|
| Q1 | 알림에 신청·회차 참조(`enrollment_id`, `session_id`)를 둘까? | **두지 않음** (요청 스키마 그대로). 발송·조회 요구가 생기면 새 V-마이그레이션으로 추가 |
| Q2 | 재신청으로 다시 확정되면 다시 알릴까? | **알림** (새 확정이므로 한 건 더) |
| Q3 | D-41(정원 증가 승급)과의 연결 | **늦게 머지되는 쪽이 연결**. FR-09가 먼저면 D-41 리스너에서 `record(…, WAITLIST_PROMOTED)` 호출을 D-41 계획에 추가하고, D-41이 먼저면 FR-09 구현에 포함 |
| Q4 | `NotificationService.record`의 트랜잭션 전파 | **MANDATORY** (트랜잭션 밖 호출을 즉시 실패시켜 "확정과 함께 저장" 규칙을 지킴) |

## 9. 결정 사항 기록 (승인 시)
- D-42: FR-09 알림 기록 방식(B안: 같은 트랜잭션 저장, 이벤트 미사용과 이유, outbox로의 확장 방향), 스키마, Q1~Q4
