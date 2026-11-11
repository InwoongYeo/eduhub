# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트

EduHub: 사내 교육과정 조회·수강 신청 시스템 (정원 초과 시 대기자 등록). 요구사항(FR·NFR)과 결정 사항(D-xx)의 기준 문서는 `docs/requirements.md`다. 새로 정한 사항은 이 문서의 결정 사항 표에 다음 D-번호로 기록하고, 바뀐 결정은 상태 열(유효/대체/완료/재검토)을 고친다.

진행 상태 (D-19): 백엔드는 새 스택·데이터 모델과 FR-01~03(과정 조회·검색, 상세·회차, 관리자 과정·회차 관리)까지 구현됐다. 수강 신청·대기·취소(FR-04~06)는 3주차, 프론트엔드(React MFE)는 아직 없다. D-02(취소 기한·재신청)는 재검토 상태다.

## 명령 (`backend/`에서 실행, JDK 21 필요)

- 실행: `./gradlew bootRun` (기본 프로필 `local`)
- dev 프로필: 루트에서 `docker compose up -d` 후 `./gradlew bootRun --args='--spring.profiles.active=dev'`
- 전체 테스트: `./gradlew test`
- 클래스 하나: `./gradlew test --tests "com.eduhub.course.service.CourseAdminServiceTest"`
- 메서드 하나: `./gradlew test --tests "com.eduhub.course.service.CourseAdminServiceTest.메서드명"`
- Swagger UI: http://localhost:8080/swagger-ui.html (`local`에서만, D-07)
- Windows PowerShell 5.1에서는 `&&` 대신 `;`, `./gradlew` 대신 `.\gradlew.bat`

## 프로필과 스키마

| 프로필 | DB | 비밀 값 |
|---|---|---|
| `local` (기본) | H2 메모리, MySQL 모드 | 계정·`JWT_SECRET` 모두 로컬 기본값 있음. `LocalDataInitializer`가 관리자·임직원 계정과 과정 3개를 만든다 |
| `dev` | docker-compose MySQL 8.4, root 계정 (D-13) | 루트 `.env`를 `spring.config.import`로 직접 읽음 (`MYSQL_*`, `JWT_SECRET`) |
| `prod` | `DB_URL` 등 환경 변수 | 전부 환경 변수, Swagger 끔 |

모든 프로필에서 스키마는 Flyway(`src/main/resources/db/migration`)가 만들고 JPA는 `ddl-auto: validate`만 한다 (D-20). 엔티티를 바꾸면 새 `V{n}__*.sql`을 추가하고 기존 마이그레이션은 고치지 않는다. SQL은 MySQL 8.4와 H2(MySQL 모드) 양쪽에서 실행돼야 한다(예: `ALTER TABLE`은 문장 하나에 변경 하나). `Instant`는 `preferred_instant_jdbc_type: TIMESTAMP`로 `DATETIME(6)`에 매핑된다. `SchemaMappingTest`가 H2에서 Flyway를 돌려 엔티티와 SQL의 일치를 검증하므로, 마이그레이션이 빠지면 여기서 깨진다(애플리케이션 컨텍스트를 띄우는 다른 테스트도 함께 깨진다).

## 백엔드 구조 (`com.eduhub`)

- 도메인별 패키지 `user`, `course`, `enrollment`, 각각 `controller`/`service`/`repository`/`domain`/`dto`. 공통은 `global.config`(보안·OpenAPI), `global.security`(JWT), `global.error`, `global.common`, `global.init`
- 호출 방향 controller → service → repository. 컨트롤러는 엔티티 대신 `dto`를 반환한다. `enrollment`가 `course`를 참조하므로 `course`는 `enrollment`를 참조하지 않는다
- 서비스 클래스에 `@Transactional(readOnly = true)`, 쓰기 메서드에만 `@Transactional`. 조회(`CourseService`)와 관리자 변경(`CourseAdminService`)을 나눈다
- 목록 API는 `Pageable`을 받아 `PageResponse.from(page)`로 반환. 페이지 크기 기본 10, 최대 100
- 엔티티: `protected` 기본 생성자, 정적 팩토리 `create(...)`, setter 없음. 상태 규칙(종료된 과정·회차는 변경 불가, 시각 순서, 정원 ≥ 확정 인원)은 엔티티 메서드가 검사하고 `BusinessException`을 던진다. 연관관계는 `@ManyToOne(fetch = LAZY)`, enum은 `@Enumerated(EnumType.STRING)`
- `spring.jpa.open-in-view: false`이므로 지연 로딩 연관은 서비스(트랜잭션 안)에서 DTO로 바꿔야 한다

### 도메인 모델

- `Course`(제목·설명·분류·상태) 1 : N `CourseSession`(회차 번호·일정·장소·정원·확정 인원·상태) 1 : N `Enrollment`(임직원·상태·신청 시각)
- 수강 신청 단위는 회차다 (D-17). `enrollment`의 (임직원, 회차) 유니크 제약이 중복 신청을 막는다
- 남은 자리는 `CourseSession.confirmedCount`로 계산한다 (D-21). 신청 행을 세지 않는다. 신청·취소 로직은 이 값을 함께 바꿔야 하고, 동시성(NFR-01)은 회차 행 잠금으로 처리할 계획이다
- 대기 순번은 같은 회차의 WAITING 중 `appliedAt` 순서
- 회차 번호는 서버가 정한다(과정 내 최대 번호 + 1). 과정을 종료하면 회차도 모두 종료된다
- 역할은 `EMPLOYEE`, `ADMIN` 두 개, 계정당 하나 (D-01, D-14)

### 인증·인가

- `POST /api/auth/login` → HS256 JWT (subject=사용자 id, `role` 클레임). Spring Security OAuth2 리소스 서버가 `Authorization: Bearer`를 검증하고 `role`을 `ROLE_` 권한으로 바꾼다. 키·만료는 `JwtProperties`(`eduhub.jwt.*`), 키는 32바이트 이상
- `SecurityConfig`: 로그인·Swagger·`GET /api/courses/**`는 공개 (D-22), `/api/admin/**`은 ADMIN, 나머지는 인증 필요
- 비밀번호는 `PasswordEncoder`(BCrypt)로 인코딩한 값만 저장한다. 회원가입 API는 없다

### 오류 처리

비즈니스 오류는 `throw new BusinessException(ErrorCode.X)`. 새 오류는 `ErrorCode`에 추가한다(접두어: 공통 `G`, 인증 `A`, 과정·회차 `C`). `GlobalExceptionHandler`가 예외를 `{code, message, timestamp}`(`ErrorResponse`)로 바꾸고, 401/403은 `SecurityConfig`의 entry point·access denied handler가 같은 형식으로 직접 쓴다.

### OpenAPI

springdoc 3.2.0. 컨트롤러에 `@Tag`, 메서드에 `@Operation(summary = ...)`. `OpenApiConfig`에 bearer 보안 스킴이 있어 Swagger의 Authorize로 토큰을 넣는다.

## 테스트

- 서비스: Mockito 단위 테스트 (`CourseServiceTest`, `CourseAdminServiceTest`)
- 컨트롤러: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional`(테스트마다 롤백). `local` 초기 데이터(과정 3개: 개발 2, 리더십 1, 기본 계정)에 의존하므로 `LocalDataInitializer`를 바꾸면 기대값도 바뀐다. 관리자 API 테스트는 `/api/auth/login`으로 실제 토큰을 받아 쓴다
- 스키마: `SchemaMappingTest` (`@DataJpaTest` + H2 + Flyway + `validate`), Docker 없이 실행
- 테스트 메서드 이름은 한글 문장 (`과정_목록은_로그인_없이_...`)

## 환경 변수와 Git

- 새 환경 변수 키는 `.env.example`에 추가한다. `.env`는 커밋하지 않는다
- `main`에서 브랜치를 만들어 작업하고 PR로 병합한다
- 커밋 메시지: `type(scope): 설명` (예: `feat(course): …`, `docs: …`, `chore(dev): …`), 본문은 한국어
