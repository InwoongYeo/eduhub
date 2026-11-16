# 설계명세: 과정 단위 관리 작업의 잠금 (D-32 후속)

- 상태: **완료** (2026-11-16 승인·구현. 락 제거 실험에서 A1 교착, A2 종료 덮어쓰기, A3 회차 번호 중복이 재현됨)
- 브랜치: `fix/course-close-lock`
- 관련: D-32(후속 과제), D-03(종료된 과정·회차는 수정 불가), D-21, NFR-01, FR-03

## 1. 문제

`CourseAdminService`가 과정(course) 행을 **잠그지 않고** 읽은 뒤, 저장할 때 모든 열을 다시 쓴다(`@DynamicUpdate`·`@Version` 없음). 같은 과정에 관리 작업이 동시에 들어오면 다음이 생긴다.

| # | 동시 작업 | 결과 | 출처 |
|---|---|---|---|
| P1 | `closeCourse` + `addSession` | MySQL 교착 → 한쪽 롤백, 관리자는 500 | D-32 (최종 재검토 W-a) |
| P2 | `closeCourse` + `addSession` | `closeCourse`가 회차를 읽은 뒤 추가된 회차가 **닫힌 과정에 OPEN으로 남음** | D-32 |
| P3 | `closeCourse` + `updateCourse` | `updateCourse`가 읽은 OPEN 상태를 저장하면서 **종료를 덮어써 과정이 다시 열림** (lost update, D-03 위반) | 이번 분석에서 발견 |
| P4 | `addSession` + `addSession` | 둘 다 같은 `max(session_no) + 1`을 계산 → `uk_course_session_no` 위반으로 500 | 이번 분석에서 발견 |

## 2. 해결: 과정 행을 먼저 잠근다

과정을 읽고 바꾸는 관리 작업은 **과정 행을 `PESSIMISTIC_WRITE`로 먼저 잠근다.** 같은 과정의 관리 작업이 줄을 서므로 P1~P4가 모두 사라진다.

| 메서드 | 지금 | 바꾼 뒤 |
|---|---|---|
| `updateCourse` | `findById` | `findByIdForUpdate` (P3) |
| `closeCourse` | `findById` → 회차 범위 잠금 | `findByIdForUpdate` → 회차 범위 잠금 (P1·P2·P3) |
| `addSession` | `findById` → 최대 번호 조회 → INSERT | `findByIdForUpdate` → 최대 번호 조회 → INSERT (P1·P2·P4). 닫힌 과정이면 기존대로 `CourseSession.create` 안의 `course.checkOpen()`이 409 (C003) |
| `createCourse` | 새 행 | 그대로 |
| `updateSession`·`closeSession` | 회차 행 잠금 | 그대로 (과정 행을 건드리지 않음) |

- `CourseRepository`에 `@Lock(PESSIMISTIC_WRITE) @Query("select c from Course c where c.id = :id") Optional<Course> findByIdForUpdate(@Param("id") Long id)` 추가
- **잠금 순서: 과정 → 회차.** 과정을 잠그는 경로(closeCourse)만 회차를 추가로 잠근다. 회차만 잠그는 경로(신청, 회차 수정·종료)는 과정 행을 잠그지 않으므로 순환 대기가 생기지 않는다
  - 신청(`enroll`)의 INSERT는 회차에 대한 외래 키만 있어 과정 행을 잠그지 않는다
  - `addSession`의 INSERT는 과정 행 외래 키 검사로 S 잠금이 필요하지만, 같은 트랜잭션이 이미 X 잠금을 가지고 있어 대기하지 않는다
- 스키마 변경 없음

## 3. 오류 응답

새 오류 코드 없음. 순서가 정해지므로 결과가 결정적이 된다.

| 상황 | 응답 |
|---|---|
| 종료된 과정에 회차 추가 / 과정 수정 | 409 C003 (기존) |
| 그 밖 | 기존과 같음 |

## 4. 테스트

### 단위 (`CourseAdminServiceTest`, Mockito)
- 스텁을 `courseRepository.findById` → `findByIdForUpdate`로 바꾼다. 검증 내용은 그대로

### 동시성 (`CourseAdminConcurrencyTest`, Testcontainers MySQL 8.4, 신규)
`EnrollmentConcurrencyTest`와 같은 방식(tmpfs, 클래스에 `@Transactional` 없음, 여러 라운드).

| # | 시나리오 | 기대 |
|---|---|---|
| A1 | 과정 하나에 회차 1개를 미리 만든 뒤 `closeCourse` 1건 + `addSession` 10건 동시 (라운드 10) | 500류 예외 0건(성공 또는 C003만), 끝나면 과정 CLOSED이고 **그 과정의 회차가 모두 CLOSED**, 성공한 `addSession` 수 + 1 == DB 회차 수 |
| A2 | `closeCourse` 1건 + `updateCourse` 10건 동시 (라운드 10) | 성공 또는 C003만, 끝나면 과정이 **CLOSED** |
| A3 | `addSession` 10건 동시 (라운드 10) | 예외 0건, 회차 번호 1~10이 중복·누락 없음 |

> 변경 이력: 1차 검토에서 A1이 종료가 먼저면 회차가 없어 빈 참으로 통과한다는 지적 → 회차 1개 선생성, 회차 수 검증, 라운드 5 → 10으로 보강 (2026-11-16)

- 테스트의 테스트: `findByIdForUpdate`의 `@Lock`을 개발자가 직접 주석 처리하면 A1~A3 중 하나 이상이 실패해야 한다 (확률적이라 여러 라운드)

## 5. 문서
- `docs/requirements.md`: D-32 상태를 **완료**로 바꾸고 결과를 적는다. P3·P4를 새 결정(D-35)으로 기록
- `docs/design/enrollment.md`: 5장 "알려진 교착" 항목에 해결됨 표시
