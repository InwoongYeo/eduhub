# backend

Spring Boot 4.1.2 · Java 21 · Gradle. 기본 패키지는 `com.eduhub`.

## 테스트 명령 (`backend/`에서)

- 전체: `./gradlew test`
- 클래스 하나: `./gradlew test --tests "com.eduhub.course.service.CourseServiceTest"`
- 서비스 테스트는 Mockito 단위 테스트, 컨트롤러 테스트는 `@SpringBootTest` + `@AutoConfigureMockMvc` (local 초기 데이터 사용)
- JDK 21이 필요하다 (`java -version`)

## 계층 (NFR-06)

- 도메인별 패키지: `user`, `course`, `enrollment`. 각각 `controller` / `service` / `repository` / `domain` / `dto`
- 공통: `global.config`(보안·OpenAPI), `global.error`, `global.common`, `global.init`
- 호출 방향은 controller → service → repository. 컨트롤러는 엔티티 대신 `dto`를 반환한다
- 서비스는 클래스에 `@Transactional(readOnly = true)`를 붙인다
- 오류는 `BusinessException(ErrorCode.X)`로 던진다. `GlobalExceptionHandler`가 `{code, message, timestamp}`로 바꾼다 (NFR-03). 새 오류는 `ErrorCode`에 추가한다
- 목록 API는 `Pageable`을 받고 `PageResponse`로 반환한다 (NFR-05)

## JPA

- 엔티티: 기본 생성자는 `protected`, 생성은 정적 팩토리(`create(...)`), setter는 두지 않는다
- 연관관계는 `@ManyToOne(fetch = FetchType.LAZY)`. 목록 조회는 `@EntityGraph`로 함께 가져와 N+1 조회를 막는다
- enum은 `@Enumerated(EnumType.STRING)`
- `spring.jpa.open-in-view: false`
- `local`: H2 메모리 DB, `ddl-auto: create-drop` / `prod`: MySQL, `ddl-auto: validate`
- 비밀번호는 `PasswordEncoder`(BCrypt)로 암호화한 값만 저장한다 (NFR-01)

## OpenAPI

- springdoc `3.2.0`. 문서 정보는 `global.config.OpenApiConfig`
- 컨트롤러에는 `@Tag`, 메서드에는 `@Operation(summary = …)`
- `prod`에서는 api-docs와 Swagger UI를 끈다 (D-07)
- JWT 인증 버튼은 2주차에 추가한다 (D-06)
