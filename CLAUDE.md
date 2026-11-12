# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 프로젝트

EduHub: 사내 교육과정 조회·수강 신청 시스템 (정원 초과 시 대기자 등록). 요구사항(FR·NFR)과 결정 사항(D-xx)의 기준은 `docs/requirements.md`다.
진행 상태 (D-19): 백엔드는 FR-01~03, 프론트엔드는 과정 목록 화면(`course-mfe`)까지. 수강 신청·대기·취소(FR-04~06)는 다음 주차.

## 디렉터리 지도

- `backend/`: Spring Boot 4.1.2 / Java 21 API 서버 (Gradle Kotlin DSL). 규칙은 `backend/CLAUDE.md`
- `frontend/`: npm workspaces. `apps/course-mfe`(React 18 + TS + MUI, 단독 실행), `packages/api-client`(OpenAPI 생성 클라이언트). 규칙은 `frontend/CLAUDE.md`
- `docs/requirements.md`: 개요, 기술 스택, FR·NFR, 결정 사항
- `docker-compose.yml`: dev 프로필용 MySQL 8.4
- `.env.example`: 환경 변수 예시 (`.env`로 복사해 사용)

## 실행 명령 (`backend/`에서, JDK 21 필요)

- 실행: `./gradlew bootRun` (기본 프로필 `local`, H2 메모리 DB)
- dev 프로필: 루트에서 `docker compose up -d` 후 `./gradlew bootRun --args='--spring.profiles.active=dev'`
- 테스트: `./gradlew test` (자세한 명령은 `backend/CLAUDE.md`)
- Swagger UI: `http://localhost:8080/swagger-ui.html` (`local`에서만, D-07)
- 프론트(`frontend/`에서, Node 필요): `npm install` → `npm run generate:api` → `npm test` / `npm run dev`(포트 3001, `/api`는 8080으로 프록시)
- Windows PowerShell 5.1에서는 `&&` 대신 `;`, `./gradlew` 대신 `.\gradlew.bat`

## 공통 규칙

- 비밀 값(비밀번호, 키, 접속 정보)은 코드와 설정 파일에 쓰지 않고 환경 변수로 받는다. `.env`는 커밋하지 않는다
- 새 환경 변수 키는 `.env.example`에 추가한다
- 새로 정한 사항은 `docs/requirements.md` 결정 사항 표에 다음 D-번호로 기록하고, 바뀐 결정은 상태 열(유효/대체/완료/재검토)을 고친다
- 작업은 `main`에서 브랜치를 만들어 하고, PR로 `main`에 병합한다
- 커밋 메시지: `type(scope): 설명` (예: `feat(course): …`, `docs: …`, `chore(dev): …`), 본문은 한국어

## 개발 진행 절차 (기능 단위)

1. 시작: `/fr-start <요구사항ID>` → 설계명세(`docs/design/`) → 개발자 승인 → 구현계획(`docs/plans/`) → 개발자 승인
2. 구현: 계획의 작업마다 `implementer`에게 맡기고, 끝나면 `reviewer`로 검토한다
   - 심각·경고는 `implementer`에게 수정시킨다. 수정·재검토는 최대 2회, 넘으면 개발자에게 보고한다
3. 최종 검토: 모든 작업이 끝나면 `critical-reviewer`에게 설계명세 + 전체 diff + 1차 검토 결과를 넘긴다
4. 서브에이전트를 호출할 때 model은 지정하지 않는다 (정의 파일의 model을 따른다)
5. 3개 이상 파일·여러 계층에 걸친 작업이나 diff 1,000줄 이상은 개발자에게 알리고 지시를 받는다
6. 수량 변경(정원, 확정 인원, 대기 순번)에는 반드시 락, 중복 방지는 DB 유일 제약으로 보장한다
7. 팀 권한은 `.claude/settings.json`(커밋), 개인 설정은 `.claude/settings.local.json`(커밋 금지)
