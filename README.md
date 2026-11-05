# EduHub

온라인 강의 수강 관리 서비스. 요구사항과 결정 사항은 [docs/requirements.md](docs/requirements.md)를 참고하세요.

## 폴더 구조

```
eduhub/
├── backend/       # Spring Boot 4.1.2 / Java 21 (Gradle)
├── docs/          # 요구사항·결정 사항
├── .env.example   # 환경 변수 예시 (복사해서 .env로 사용)
├── .gitignore
└── README.md
```

## 시작하기

준비물: JDK 21

1. `.env.example`을 복사해 `.env`를 만들고 값을 채웁니다 (prod 프로필에서 필요).

   ```bash
   cp .env.example .env
   ```

2. 백엔드를 실행합니다. 기본 프로필은 `local`(H2 메모리 DB)입니다.

   ```bash
   cd backend
   ./gradlew bootRun
   ```

3. API 문서: http://localhost:8080/swagger-ui.html (`local`에서만)

테스트: `cd backend && ./gradlew test`

> `.env`에는 비밀 값이 들어가므로 Git에 올리지 않습니다 (`.gitignore`에 포함).
