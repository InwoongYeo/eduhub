# EduHub

온라인 강의 수강 관리 서비스. 강사가 강의를 개설하고 학생이 수강 신청하며 관리자가 사용자와 강의를 관리한다.
요구사항(FR·NFR)과 결정 사항(D-xx)의 기준은 `docs/requirements.md`다.

## 지도

- `backend/`: Spring Boot 4.1.2 / Java 21 API 서버. 자세한 규칙은 `backend/CLAUDE.md`
- `frontend/`: 프론트엔드. 자세한 규칙은 `frontend/CLAUDE.md`
- `docs/requirements.md`: 개요, 기술 스택, FR·NFR, 결정 사항
- `.env.example`: 환경 변수 예시 (`.env`로 복사해 사용)

## 실행 명령

- 백엔드 실행: `cd backend && ./gradlew bootRun` (기본 프로필 `local`, H2 메모리 DB)
- 백엔드 테스트: `cd backend && ./gradlew test`
- API 문서: `http://localhost:8080/swagger-ui.html` (`local`에서만)
- Windows PowerShell 5.1에서는 `&&` 대신 `;`: `cd backend; .\gradlew.bat test`

## 공통 규칙

- 비밀 값(비밀번호, 키, 접속 정보)은 코드와 설정 파일에 쓰지 않고 환경 변수로 받는다 (NFR-04)
- 새 환경 변수 키는 `.env.example`에 추가한다. `.env`는 커밋하지 않는다
- 새로 정한 사항은 `docs/requirements.md`의 결정 사항에 다음 D-번호로 기록한다
- 작업은 `main`에서 브랜치를 만들어 하고, PR로 `main`에 병합한다
- 커밋 메시지 형식: `type(scope): 설명` (예: `feat(week1): …`, `docs: …`, `chore: …`)
