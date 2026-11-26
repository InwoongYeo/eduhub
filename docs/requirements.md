# EduHub 요구사항

## 개요

EduHub는 **사내 교육과정 조회·수강 신청 시스템**이다. 임직원이 사내 교육과정을 조회·신청하고, 정원이 차면 대기자로 등록된다 (D-14).

**사용자**

- **임직원(수강생)**: 교육과정을 찾아 회차에 수강 신청한다.
- **교육 담당자(관리자)**: 과정과 회차를 운영하고 수료 처리한다.

## 기술 스택

| 구분 | 기술 |
|---|---|
| 백엔드 | Java 21, Spring Boot 4.1.2 (D-09, D-16), Spring Data JPA, Flyway, springdoc-openapi |
| 데이터 | MySQL 8.4 (D-23) |
| 프론트엔드 | React 18 + TypeScript, Module Federation(shell, course-mfe, enrollment-mfe), MUI, Axios |
| 빌드 | Gradle(Kotlin DSL), npm workspaces |

> 백엔드는 이 스택으로 옮겼다(2주차). 프론트엔드는 아직 없다 (D-19).

## 기능 요구사항 (FR)

| ID | 요구사항 | 대상 | 우선순위 | 주차 |
|---|---|---|---|---|
| FR-01 | 교육과정 목록을 조회하고 제목·분류로 검색한다 (페이지 단위) | 임직원 | 필수 | 2 |
| FR-02 | 과정 상세와 회차(일정, 장소, 정원, 남은 자리)를 조회한다 | 임직원 | 필수 | 2 |
| FR-03 | 관리자는 과정과 회차를 등록·수정·종료한다 | 관리자 | 필수 | 2 |
| FR-04 | 회차에 수강 신청한다. 같은 회차에 중복 신청할 수 없다 | 임직원 | 필수 | 3 |
| FR-05 | 정원이 차면 대기자로 등록되고 대기 순번을 알려 준다 | 임직원 | 필수 | 3 |
| FR-06 | 신청을 취소하면 대기 1순위가 자동으로 신청 확정된다 | 임직원 | 필수 | 3 |
| FR-07 | 내 신청 내역(확정, 대기, 취소, 수료)을 조회한다 | 임직원 | 필수 | 4 |
| FR-08 | 관리자는 회차별 신청자·대기자 명단을 보고 수료 처리한다 | 관리자 | 보통 | 4 |
| FR-09 | 신청 확정·대기 승급 시 알림을 남긴다 (처음에는 로그·DB 기록) | 임직원 | 선택 | 5 |

## 비기능 요구사항 (NFR)

| ID | 구분 | 요구사항 | 검증 방법 |
|---|---|---|---|
| NFR-01 | 정합성 | 동시에 100명이 마지막 1자리에 신청해도 정원을 넘지 않는다 | 동시성 테스트 |
| NFR-02 | 성능 | 과정 목록 API는 데이터 1만 건에서 응답 300ms 이내, N+1 쿼리 없음 | 쿼리 로그, 성능 테스트 |
| NFR-03 | 보안 | 로그인한 사용자만 신청 가능, 본인 신청만 취소 가능, 비밀 정보는 저장소에 두지 않음 | 테스트, 비밀 검사 |
| NFR-04 | API 계약 | 모든 API는 OpenAPI 문서가 있고, 프론트 클라이언트는 스펙에서 생성한다 | Swagger UI, 생성 스크립트 |
| NFR-05 | 품질 | 서비스 계층 테스트 커버리지 70% 이상, CI에서 빌드·테스트·포맷 검사 | CI 결과 |
| NFR-06 | 운영성 | 개발·테스트·운영 설정 분리, 사람이 직접 실행할 수 있는 빌드·배포·롤백 절차 문서 | 런북, 핫픽스 훈련 |
| NFR-07 | 유지보수성 | 도메인별 패키지(course, enrollment), 계층 규칙 준수, 화면은 MFE로 분리 | 리뷰, 아키텍처 테스트 |
| NFR-08 | 이해도 | 팀원 누구나 AI 없이 구조와 핵심 파일의 역할을 설명할 수 있다 | 발표 시 확인 |

## 결정 사항

상태: **유효** 지금도 따른다 · **대체** 뒤의 결정으로 바뀌었다 · **완료** 이미 반영된 1회성 결정 · **재검토** 새 요구사항에 맞게 다시 정해야 한다

| ID | 질문 | 결정 | 관련 | 상태 |
|---|---|---|---|---|
| D-01 | 한 계정이 강사이면서 학생일 수 있는가 | 계정당 역할은 하나 | NFR-03 | 유효 (역할 목록은 D-14로 대체) |
| D-02 | 수강 취소 기한과 재신청 | 확정·대기 신청 모두 회차 시작 시각(`starts_at`) 전까지 취소 가능. 대기 1순위 자동 승급(FR-06)도 이 기한까지만 일어나고, 기한 뒤 남은 대기자는 대기 상태로 남는다. 취소 후 같은 회차 재신청은 새 신청처럼 처리한다: 자리가 있으면 확정, 없으면 대기 맨 뒤(이전 순번은 되돌리지 않음). 임직원·회차당 한 행은 유지하고 그 행의 상태와 `applied_at`을 갱신한다 | FR-04~06 | 완료 (FR-04 재신청, FR-06 취소·승급으로 구현, D-37) |
| D-03 | 수강생이 있는 강의 삭제 | 과정·회차는 삭제하지 않는다. FR-03의 "종료"는 상태를 CLOSED로 바꾸는 것이고, 과정을 종료하면 그 회차도 모두 종료된다. 종료된 과정·회차는 수정할 수 없다 | FR-03 | 유효 (2주차에 정리) |
| D-04 | 최초 관리자 계정 생성 | `local`은 초기 데이터, `prod`는 환경 변수로 생성 | FR-03, NFR-06 | 유효 |
| D-05 | 강의 검색 범위 | 제목과 강사 이름 | FR-01 | 대체 (D-18) |
| D-06 | JWT 발급·검증 시점 | 2주차에 구현. `POST /api/v1/auth/login`이 HS256 JWT(subject=사용자 id, `role` 클레임)를 발급하고, Spring Security 리소스 서버가 검증한다. 키는 `JWT_SECRET`(local만 기본값). 회원가입은 없다 | NFR-03 | 완료 |
| D-07 | prod에서 Swagger UI | 끈다 (`local`에서만 노출) | NFR-04, NFR-06 | 유효 |
| D-08 | 1주차 NFR 반영 범위 | 보안 뼈대, API 문서, 품질(오류 형식·테스트 환경), 프로필, 패키지 구조. 성능은 페이지 단위 설계만 | - | 완료 |
| D-09 | Spring Boot 버전 | 4.1.2 (3.x는 무료 지원 종료, Initializr도 4.x만 제공) | 기술 스택 | 유효 (D-16) |
| D-10 | 개발 DB와 스키마 관리 | dev 프로필은 docker-compose MySQL 8.4, 접속 정보는 `.env`. 스키마는 Flyway가 만들고 JPA는 `validate`. local(H2)은 기존대로 JPA가 생성 | NFR-06 | 유효 (local 스키마 부분만 D-20으로 대체) |
| D-11 | course_session의 의미 | 강의의 수업 회차. 강의 1개에 여러 회차(회차 번호, 제목, 시작·종료 시각). 수강 신청은 강의 단위 유지 | FR-02 | 대체 (신청 단위는 D-17) |
| D-12 | 마이그레이션 분리 | V1: course·course_session, V2: users·enrollment와 course.instructor_id 외래 키 | D-10 | 완료 (V3로 새 모델 전환, D-20) |
| D-13 | dev MySQL 계정 | 개발용은 root 계정 사용, 비밀번호는 `.env`의 `MYSQL_ROOT_PASSWORD`. 운영(prod)은 별도 계정(`DB_USERNAME`) | NFR-06 | 유효 (D-23) |
| D-14 | 서비스 범위와 사용자 | 온라인 강의 수강 관리 → 사내 교육과정 조회·수강 신청(대기자 포함). 역할은 임직원·관리자 2개 (강사 역할 없음) | 개요 | 유효 |
| D-15 | 데이터베이스 | MySQL → PostgreSQL. dev 환경(docker-compose, `.env`)과 Flyway SQL을 PostgreSQL 기준으로 바꾼다 | 기술 스택, NFR-06 | 대체 (D-23) |
| D-16 | 요구사항 초안의 "Spring Boot 3" 표기 | D-09대로 4.1.2 유지. 초안 표기를 정정 | 기술 스택 | 유효 |
| D-17 | 수강 신청 단위 | 강의(과정) 단위 → 회차 단위. 중복 신청 방지·정원·대기 순번 모두 회차 기준 | FR-04~06 | 유효 |
| D-18 | 과정 검색 범위 | 제목은 부분 일치(`keyword`), 분류는 정확히 일치(`category`). 둘 다 생략 가능 | FR-01 | 유효 |
| D-19 | 새 요구사항의 코드 반영 시점 | 2주차에 백엔드 기반(스택, 역할, 데이터 모델)과 FR-01~03을 반영했다. FR-04 이후와 프론트엔드는 FR 표의 주차대로 | 전체 | 유효 |
| D-20 | 새 모델의 마이그레이션 | 이미 적용된 V1·V2는 고치지 않고 `V3__session_enrollment_model.sql`로 새 모델(과정 분류, 회차 장소·정원·확정 인원·상태, 회차 단위 신청, 역할 2개)로 바꾼다. 강의 단위 신청은 회차로 옮길 수 없어 enrollment 테이블은 새로 만든다. local도 H2(MySQL 모드) + Flyway + `validate`로 바꿔 모든 프로필의 스키마 경로를 하나로 한다(D-10의 local 부분 대체) | D-10, D-12 | 유효 |
| D-21 | 남은 자리 계산 | 회차에 확정 인원(`confirmed_count`)을 두고 남은 자리 = 정원 - 확정 인원. 목록·상세에서 신청 행을 세지 않고, 3주차 동시성 제어(NFR-01)는 이 회차 행을 잠가 처리한다. DB에 `0 ≤ confirmed_count ≤ capacity` 검사 제약. 정원은 확정 인원보다 적게 줄일 수 없다 | FR-02, FR-05, NFR-01 | 유효 |
| D-22 | 조회 API 인증 | 과정 목록·상세 조회(GET `/api/v1/courses/**`)는 로그인 없이 허용. 과정·회차 등록·수정·종료(그 밖의 `/api/v1/courses/**`, `/api/v1/sessions/**`)는 ADMIN만, 그 밖의 API는 로그인 필요 (경로는 D-24) | FR-01~03, NFR-03 | 유효 |
| D-23 | 데이터베이스 (D-15 번복) | PostgreSQL로 바꾸지 않고 MySQL 8.4를 유지한다. dev는 docker-compose MySQL + root 계정(D-13) 그대로 | 기술 스택, NFR-06 | 유효 |
| D-24 | API 경로와 목록 응답 (3회차 자료 반영) | 모든 API를 `/api/v1` 아래로 옮긴다. 조회는 `GET /api/v1/courses`, `GET /api/v1/courses/{courseId}`, 관리는 `POST /api/v1/courses`, `PUT /api/v1/courses/{courseId}`, `POST /api/v1/courses/{courseId}/sessions`, `POST /api/v1/courses/{courseId}/close`, 회차 수정·종료는 `PUT /api/v1/sessions/{sessionId}`, `POST /api/v1/sessions/{sessionId}/close`, 로그인은 `POST /api/v1/auth/login`. 목록 항목(`CourseSummaryResponse`)에 회차 수 `sessionCount`를 넣고, 목록 조회 뒤 한 번의 집계 쿼리로 채운다(N+1 없음). 관리 API는 D-22대로 ADMIN 권한을 유지한다 | FR-01~03, NFR-02, NFR-04 | 유효 |
| D-25 | 프론트에서 백엔드 호출 방식 (CORS) | 백엔드 CORS 설정 대신 Vite 개발 서버 프록시(`/api` → `localhost:8080`)를 쓴다. 생성 클라이언트는 `basePath: ''`로 같은 출처를 호출한다. 운영 배포 방식은 MFE 연결(6주차) 때 다시 정한다. 생성 원본 `packages/api-client/openapi.json`은 커밋하고, 생성 코드(`src/generated`)는 커밋하지 않는다 | FR-01, NFR-04 | 유효 |
| D-26 | Claude Code 팀 권한·스킬·서브에이전트 (4회차 자료 반영) | 팀 권한은 `.claude/settings.json`에 둔다. allow: 테스트·빌드·API 클라이언트 생성·git 조회, ask: `git push`·`docker`, deny: 비밀 파일 읽기·강제 푸시·`flywayClean`·`ssh`·`mysql` 직접 접속·`application-prod.yml` 읽기. `.env.example`은 Claude가 새 키를 추가해야 하므로 자료의 `Read(./.env.*)` 대신 `.env`, `.env.local`, `.env.*.local`, `.env.dev`, `.env.prod`만 막는다. Windows PowerShell 명령(`.\gradlew.bat`)도 같은 규칙을 둔다. 스킬 `eduhub-api`(Claude가 사용), `/fr-start`(사람만 호출), 서브에이전트 `implementer`·`reviewer`(Sonnet), `critical-reviewer`(Opus). 진행 절차는 루트 `CLAUDE.md` | NFR-03, NFR-08 | 유효 |
| D-27 | 동시성 테스트 DB (4회차 자료와 다름) | 동시성 테스트(NFR-01)는 자료의 Testcontainers PostgreSQL 대신 Testcontainers MySQL 8.4로 실행한다(D-23 유지). H2는 락 동작이 달라 동시성 검증에 쓰지 않는다. MySQL에는 부분 유일 인덱스가 없으므로, 중복 신청 방지는 기존 (임직원, 회차) 유일 제약과 D-02의 "한 행을 갱신하는" 재신청 규칙으로 보장한다. 이 테스트를 돌리려면 Docker가 필요하다 | NFR-01, FR-04 | 유효 |
| D-28 | 수강 신청 API 경로 | `POST /api/v1/sessions/{sessionId}/enrollments` (요청 본문 없음, 신청자는 JWT subject). `/api/v1/sessions/**`는 ADMIN 전용(D-22)이므로 SecurityConfig에 이 경로 규칙을 ADMIN 규칙보다 앞에 둔다. 설계: `docs/design/enrollment.md` | FR-04, NFR-03, D-22, D-24 | 유효 |
| D-29 | 수강 신청 가능 역할 | `EMPLOYEE`만. ADMIN은 403 | FR-04, D-14 | 유효 |
| D-30 | 신청 기한 | 회차가 `OPEN`이고 현재 시각이 `starts_at` 전일 때만 신청할 수 있다 (D-02의 취소 기한과 같은 기준). 종료된 회차와 시작한 회차는 409로 구분한다 | FR-04, D-02 | 유효 |
| D-31 | 정원이 찼을 때 FR-04의 동작 | `WAITING`으로 저장하고 응답에는 상태만 준다. 대기 순번(`waitingNo`)은 FR-05에서 응답에 추가한다 | FR-04, FR-05 | 완료 (FR-05에서 `waitingNo` 추가, D-36) |
| D-32 | 과정 종료와 회차 추가의 동시 실행 (FR-04 최종 재검토 W-a) | (FR-04 PR 당시) FR-04 PR에서는 고치지 않고 후속 과제로 둔다. 당시: 같은 과정에 `closeCourse`(회차 범위 잠금)와 `addSession`(course 행 S 잠금 후 INSERT)이 동시에 실행되면 MySQL 교착으로 한쪽이 롤백되어 500을 받는다(데이터는 깨지지 않음). 또 `closeCourse`가 읽은 뒤 추가된 회차는 닫힌 과정에 OPEN으로 남을 수 있다. 후속: 두 경로 모두 Course 행을 먼저 `PESSIMISTIC_WRITE`로 잠그고(순서 course → 회차), `closeCourse` 동시성 테스트를 추가한다. **결과(완료)**: `CourseRepository.findByIdForUpdate`로 `updateCourse`·`closeCourse`·`addSession`이 과정 행을 먼저 잠근다(잠금 순서 과정 → 회차). `CourseAdminConcurrencyTest`로 검증. 설계: `docs/design/course-close-lock.md` | FR-03, NFR-01 | 완료 |
| D-33 | 서버 상태 확인 API (2회차 1주차 범위 보완) | 자료의 `GET /api/health` 대신 D-24에 맞춰 `GET /api/v1/health`로 둔다. 로그인 없이 호출할 수 있고 `{"status": "UP"}`만 돌려준다(DB 등 의존 시스템 상태는 보지 않음). 위치는 `global.health.HealthController` | NFR-06, D-24 | 유효 |
| D-34 | 코드 포맷 도구 (2회차 1주차 범위 보완) | Spotless(Gradle 플러그인 8.11.0)를 쓴다. 기존 스타일(탭 들여쓰기)을 바꾸지 않도록 Java는 안 쓰는 import 제거·줄 끝 공백 제거·파일 끝 줄바꿈만, Gradle 스크립트는 줄 끝 공백·파일 끝 줄바꿈만 검사한다. 줄바꿈 문자는 Git 설정(`core.autocrlf`)을 따른다. `./gradlew spotlessApply`로 고치고, `./gradlew build`의 check에서 `spotlessCheck`가 실행된다. 들여쓰기 규칙을 바꾸는 포매터(google-java-format 등)는 전체 재포맷이 필요해 지금은 넣지 않는다 | NFR-05 | 유효 |
| D-35 | 과정 단위 관리 작업의 직렬화 (D-32 분석 중 발견) | 과정을 읽고 바꾸는 관리 작업(과정 수정·종료, 회차 추가)은 과정 행을 `PESSIMISTIC_WRITE`로 먼저 잠근다. 이로써 D-32의 교착 외에, 과정 수정이 동시에 실행된 종료를 덮어써 과정이 다시 열리는 문제(lost update, D-03 위반)와 동시 회차 추가가 같은 회차 번호를 계산해 유일 제약 위반(500)이 나는 문제도 막는다. 회차만 바꾸는 경로(신청, 회차 수정·종료)는 과정 행을 잠그지 않아 잠금 순서(과정 → 회차)가 뒤집히지 않는다 | FR-03, NFR-01, D-03, D-32 | 유효 |
| D-36 | 대기 순번 (FR-05) | 순번은 저장하지 않고 계산한다: 같은 회차의 `WAITING`을 `applied_at` 오름차순, 같으면 `id` 오름차순으로 줄 세운 위치(1부터). 취소·승급(FR-06) 뒤 자동으로 당겨지고, 재신청은 `applied_at` 갱신으로 맨 뒤(D-02). 신청 응답에 `waitingNo`(대기 중일 때만, 그 밖은 null)를 넣고, 내 신청 상태 조회 `GET /api/v1/sessions/{sessionId}/enrollments/me`(EMPLOYEE, 없으면 404 E004)를 둔다. 시계 역전(`Clock.system`이 뒤로 감)은 보정하지 않는다(드물고 영향은 순번 한 칸). 전제: 신청에서 회차 잠금 앞에 일반 SELECT를 두지 않는다(REPEATABLE READ 스냅숏 시점). FR-06 규칙: 승급 대상은 `WAITING`을 `applied_at, id` 순으로 정렬한 첫 행이고, 취소·승급도 회차 행을 먼저 잠근다. 설계: `docs/design/waiting-no.md` | FR-05, NFR-01, D-02, D-31 | 유효 |
| D-37 | 신청 취소와 자동 승급 (FR-06) | `POST /api/v1/sessions/{sessionId}/enrollments/me/cancel`(EMPLOYEE, 본인 신청만, 200으로 취소된 내 신청을 돌려주고 승급자 정보는 주지 않음). 확정·대기 신청만 취소할 수 있고(그 밖 409 E005), 회차가 종료됐거나 시작한 뒤에는 409 E006(D-02, 종료 회차도 불가). 확정자가 취소하면 대기 1순위(`applied_at, id` 순 첫 행, D-36)를 CONFIRMED로 바꾸고 확정 인원은 그대로, 대기자가 없으면 확정 인원 −1. 취소도 회차 행을 먼저 잠근다(D-35). 정원을 늘렸을 때 대기자 자동 승급은 하지 않는다 — **후속 과제** (지금은 새 신청자가 빈자리를 먼저 차지할 수 있고, 대기자가 취소 후 재신청으로 대기열을 건너뛸 수 있음). 사용자 비활성화 기능이 생기면 승급 시 비활성 대기자 처리(건너뛰기 등)를 정한다 — **후속 과제** (승급 알림(D-42) 포함: 지금은 비활성 대기자도 승급되고 `WAITLIST_PROMOTED`가 기록된다. 발송이 붙기 전에 정할 것). 설계: `docs/design/cancel-promotion.md` | FR-06, NFR-01, NFR-03, D-02, D-36 | 유효 |
| D-38 | 과정 관리 화면 (FR-03 UI) | `course-mfe` 안에 [과정 조회] [과정 관리] 탭을 두고(라우터 없음, 주소 기반 라우팅은 shell·MFE 연결 때), 관리 탭은 관리자 로그인 후 과정 목록(과정명·상태 필터)·등록·수정·종료와 과정 상세의 회차 추가·수정·종료를 제공한다. 로그인 토큰은 `sessionStorage`에 두고(새로 고침 유지, 탭 닫으면 삭제) 관리용 `CourseApi`가 Bearer로 붙인다. ADMIN이 아니면 사용 불가, 401이면 다시 로그인. `sessionStorage`는 XSS에 노출되므로 운영 인증 방식(쿠키·BFF 등)은 MFE 연결·보안 작업 때 다시 정한다. 목록 API `GET /api/v1/courses`에 선택 파라미터 `status`(OPEN·CLOSED) 추가. **후속 과제**(최종 검토): 409 등 실패 뒤 목록·상세 재조회(동시 편집 시 오래된 화면), 4가지 밖 분류를 가진 기존 과정 수정 시 선택지 보정, 빠른 두 번 클릭 중복 제출 방지, 마지막 페이지 비었을 때 페이지 보정, 403이면 로그인 화면으로. 설계: `docs/design/admin-ui.md` | FR-03, FR-01, D-06, D-22, D-25 | 유효 |
| D-39 | Claude Code 훅 (5회차 자료 반영) | 팀 훅은 `.claude/hooks/*.sh`에 두고 `.claude/settings.json`의 `hooks`에 `bash "$CLAUDE_PROJECT_DIR/.claude/hooks/…"`로 등록한다(Windows는 실행 비트가 듣지 않으므로 bash로 직접 호출, 인덱스에는 `100755`). 입력 JSON은 jq로 읽고, jq가 없으면 sed로 읽는 대체 경로를 둔다. ① PostToolUse(`Edit\|Write`) `format.sh`: `*.java`는 `spotlessApply`(D-34), `*.ts`·`*.tsx`는 frontend 로컬 prettier로 그 파일만. `npx`로 내려받지 않으며, 도구가 없거나 실패해도 막지 않는다(exit 0). ② PreToolUse(`Bash\|PowerShell`) `guard-bash.sh`: 운영 호스트·DB 이름(`eduhub-prod`, `prod-db`), `flywayClean`, 강제 푸시(`--force`, `-f`, 묶은 플래그, `+refspec`), main 직접 푸시를 exit 2로 막고 이유·대안을 알린다. 일반 명령을 느리게 하지 않도록 문자열 검사만 하므로 main에서 대상 없이 `git push`, 변수·별칭 우회는 못 막는다(D-26 deny 규칙과 함께 쓰는 보조 장치). ③ PreToolUse(`Edit\|Write`) `protect-files.sh`: `application-prod.yml`(D-07), `.env`·`.env.*`(`.env.example` 제외, D-26), 이미 있는 `db/migration/V*.sql`(D-20), `api-client/src/generated/`(D-25) 수정을 막는다. 경로는 역슬래시·대소문자를 정규화한다. 셸 명령(`sed -i`, `Set-Content` 등)으로 쓰는 것은 막지 못한다. ④ Stop `stop-test.sh`(timeout 300초): `backend/src`에 커밋 안 된 변경이 있으면 `./gradlew test -q`를 돌리고, 실패하면 실패 테스트·예외 20줄을 `{"decision":"block"}`으로 돌려 Claude가 고치게 한다. `-q`는 테스트별 FAILED 줄을 찍지 않으므로 JUnit XML에서 고른다. `stop_hook_active`면 바로 통과해 무한 반복을 막는다. Docker가 없으면 `EnrollmentConcurrencyTest`(D-27) 때문에 한 번 막힌다. 훅 스크립트는 `.gitattributes`로 LF를 고정한다(CRLF면 bash 실패) | NFR-03, NFR-05, NFR-06, D-07, D-20, D-25, D-26, D-34 | 유효 |
| D-40 | Claude Code의 DB 조회 (MySQL MCP 서버) | D-26대로 `mysql` 직접 접속은 막아 두고, 개발 DB 조회는 프로젝트 `.mcp.json`의 MCP 서버 `eduhub-db`(stdio, `@benborla29/mcp-server-mysql` 버전 고정 2.0.9, INSERT·UPDATE·DELETE·DDL 플래그 모두 `false`, `MYSQL_DB` 명시로 단일 DB 모드)로만 한다. 자료의 `postgres-mcp`(restricted)는 D-23(MySQL 유지)과 맞지 않아 쓰지 않고, 이름·stdio·읽기 전용 원칙만 따른다. 자료처럼 접속 정보를 URL 변수 하나로 받지 않는 이유는 이 서버의 `MYSQL_CONNECTION_STRING`이 URL이 아니라 mysql CLI 인자 형식(`-h … -u … -p비밀번호 db`)이기 때문이다. 대상은 dev 프로필의 docker-compose MySQL(127.0.0.1)뿐이고 운영 DB에는 연결하지 않는다. 접속 계정은 읽기 전용 `eduhub_readonly`(`eduhub.*`에 SELECT·SHOW VIEW만, `MAX_USER_CONNECTIONS 5`)이며 각자 `scripts/setup-db-readonly.ps1`로 만든다(비밀번호를 사용자 환경 변수에서 읽어 계정 생성·비밀번호 맞춤·권한·시간 제한을 한 번에 하고, 다시 실행해도 안전하다. root 비밀번호는 mysql이 직접 묻는다). 쿼리 시간 제한은 MySQL에 계정 옵션(`MAX_STATEMENT_TIME`은 MariaDB 전용)이 없으므로 `SET PERSIST init_connect = 'SET SESSION max_execution_time = 10000'`(10초)으로 건다. `init_connect`는 CONNECTION_ADMIN이 있는 root에는 적용되지 않으므로 root와 이 계정만 쓰는 dev DB에서는 이 계정에만 걸린다. 계정 이름으로 분기하는 `IF(…, 10000, @@GLOBAL.max_execution_time)`는 결과 타입이 정수가 아니라 ERROR 1232로 `init_connect`가 실패하고 접속이 끊기므로 쓰지 않는다. root 외 계정을 dev DB에 추가하면 그 계정에도 10초 제한이 걸린다는 점에 주의한다. 비밀번호는 `.mcp.json`에 쓰지 않고 OS 사용자 환경 변수 `MYSQL_READONLY_PASSWORD`로 받는다(`.mcp.json`은 `.env`를 읽지 않음). 기본값 없는 변수가 비어 있으면 Claude Code가 `.mcp.json` 전체를 해석하지 못하므로 `${MYSQL_READONLY_PASSWORD:-}`로 두어, 설정하지 않은 사람은 `eduhub-db`만 접속에 실패하게 한다. 서버의 연결 풀은 최대 10개(설정 불가)라 동시 쿼리가 5개를 넘으면 계정 제한에 걸릴 수 있다. 실행 명령은 Windows 기준 `cmd /c npx`이므로 다른 OS는 로컬 범위에서 덮어쓴다 | NFR-03, NFR-06, D-13, D-23, D-26 | 유효 |
| D-42 | 신청 확정·승급 알림 기록 방식 (FR-09) | 신청이 확정될 때(`enroll` 결과 CONFIRMED, 재신청 포함)와 대기자가 승급될 때(확정자 취소로 대기 1순위 승급) 해당 임직원에게 알림을 `notification` 테이블(V4: id, employee_id → users FK, type `ENROLLMENT_CONFIRMED`·`WAITLIST_PROMOTED`, message, created_at, `(employee_id, created_at)` 인덱스)에 남기고 INFO 로그(type·employeeId·sessionId만, 개인정보 없음)를 쓴다. 대기 등록·그 밖의 취소·거절에는 남기지 않는다. **이벤트를 쓰지 않고** `EnrollmentService`가 같은 트랜잭션에서 `NotificationService.record`(전파 `MANDATORY`)를 직접 호출한다: 확정과 기록이 항상 함께 커밋·롤백되어 "확정됐는데 기록이 없는" 경우가 없고, 커밋 뒤 리스너(`AFTER_COMMIT` + `REQUIRES_NEW`)의 누락·커밋 후 예외 문제가 없다. 비용은 회차 잠금 안 INSERT 1건. 기록은 회차 잠금 뒤에만 일어나 D-36 스냅숏 전제를 지킨다. 메일·메신저 등 실제 발송이 생기면 이 테이블을 보낼 목록(outbox)으로 쓰고 커밋된 행을 읽는 별도 작업이 발송한다(그때 다시 결정). 알림에 신청·회차 참조 컬럼은 두지 않는다. 정원 증가 승급(D-41, 별도 브랜치 `feature/d41-capacity-promotion`에서 설계·계획 승인 단계라 이 표에는 아직 없음)은 늦게 머지되는 쪽이 같은 `record(…, WAITLIST_PROMOTED)`를 연결한다 — FR-09가 먼저 머지되므로 D-41 구현에 포함한다. 사용자를 지우는 테스트 정리는 알림을 먼저 지운다(FK). 검증: 롤백 통합 테스트 N2는 `record`를 `REQUIRES_NEW`로 바꾸면 실패함을 확인. 설계: `docs/design/notification.md` | FR-09, NFR-01, NFR-03, D-20, D-36, D-41 | 유효 |
