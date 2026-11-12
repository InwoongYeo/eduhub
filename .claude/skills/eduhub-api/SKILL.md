---
name: eduhub-api
description: EduHub 백엔드에 REST API를 추가하거나 바꿀 때 사용한다. "API 만들어줘", "엔드포인트 추가", "수강 신청 API", 새 기능의 백엔드(엔티티·DTO·서비스·컨트롤러·마이그레이션) 작업에 쓴다. 프론트 화면만 바꾸는 작업에는 쓰지 않는다.
---

# EduHub API 추가 절차

`backend/CLAUDE.md`의 규칙을 기준으로 아래 순서대로 진행한다. 기준 예시는 `com.eduhub.course` 패키지다.

## 1. 스키마 (필요할 때만)
- `backend/src/main/resources/db/migration`에 다음 번호 `V{n}__설명.sql`을 추가한다. 기존 파일은 고치지 않는다
- MySQL 8.4와 H2(MySQL 모드) 양쪽에서 실행되는 SQL만 쓴다. `ALTER TABLE` 하나에 변경 하나
- 새 외래 키에는 인덱스를 함께 만든다. MySQL에는 부분 인덱스(`WHERE`)가 없으므로 쓰지 않는다

## 2. 엔티티 (`<도메인>.domain`)
- `protected` 기본 생성자, 정적 팩토리 `create(...)`, setter 없음
- 상태 변경은 엔티티 메서드로 하고, 규칙 위반은 그 안에서 `BusinessException(ErrorCode.X)`로 막는다
- `@ManyToOne(fetch = FetchType.LAZY)`, enum은 `@Enumerated(EnumType.STRING)`

## 3. DTO (`<도메인>.dto`)
- `record`로 만든다. 요청은 Bean Validation(`@NotBlank`, `@Size`, `@NotNull`, `@Positive` …)
- 응답은 정적 팩토리 `from(엔티티)` 또는 `of(...)`. 엔티티를 그대로 반환하지 않는다
- 생성 클라이언트에서 필수 필드가 되도록 응답 필드에 `@Schema(requiredMode = REQUIRED)`

## 4. Repository (`<도메인>.repository`)
- Spring Data JPA. 목록 검색은 `Specification`, 집계는 한 번의 JPQL 쿼리로 (N+1 금지)
- 수량(정원·확정 인원·대기 순번)을 바꾸는 흐름은 회차 행을 잠그는 조회(`@Lock(PESSIMISTIC_WRITE)`)를 쓴다

## 5. Service (`<도메인>.service`)
- 클래스에 `@Transactional(readOnly = true)`, 쓰기 메서드에만 `@Transactional`
- 없는 대상은 `orElseThrow(() -> new BusinessException(ErrorCode.X_NOT_FOUND))`. `Optional.get()` 금지
- 잠금 조회 → 확인 → 저장은 한 트랜잭션 안에서. 트랜잭션 안에서 외부 호출 금지
- 새 오류는 `ErrorCode`에 추가한다 (접두어: 공통 `G`, 인증 `A`, 과정·회차 `C`, 수강 신청 `E`). 404·409 등 HTTP 상태를 함께 정한다

## 6. Controller (`<도메인>.controller`)
- 경로는 `/api/v1` 아래 (D-24). 권한이 필요하면 `SecurityConfig` 규칙을 함께 고친다 (D-22)
- 클래스에 `@Tag`, 메서드에 `@Operation(summary = …)`, 생성은 `@ResponseStatus(HttpStatus.CREATED)`
- 오류 응답은 `@ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))`처럼 문서화한다
- 로그인 사용자 ID는 요청 본문이 아니라 인증 정보(JWT)에서 꺼낸다 (NFR-03)

## 7. 테스트
- 서비스: Mockito 단위 테스트 (성공, 실패, 경계값)
- 컨트롤러: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional`. 인증이 필요하면 `/api/v1/auth/login`으로 받은 토큰을 쓴다
- 엔티티·SQL을 바꿨으면 `SchemaMappingTest`가 통과해야 한다
- 테스트 메서드 이름은 한글 문장

## 8. 프론트 클라이언트 갱신 (API 계약이 바뀌었을 때)
- 백엔드를 실행한 상태에서 `frontend/`에서 `npm run refresh:spec -w packages/api-client`로 `openapi.json`을 갱신하고 커밋한다
- `npm run generate:api` 후 `npm test`

## 9. 검증 (끝내기 전에 반드시)
- `backend/`에서 `./gradlew test` 전체 통과 (Windows PowerShell: `.\gradlew.bat test`)
- 새로 정한 사항은 `docs/requirements.md` 결정 사항 표에 다음 D-번호로 기록
