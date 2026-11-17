# 설계명세: 과정 관리 화면 (FR-03 UI)

- 상태: **완료** (2026-11-16 승인, 2026-11-17 구현·브라우저 확인. 후속 과제는 D-38)
- 브랜치: `feature/fr-03-admin-ui`
- 관련: FR-03(관리자 과정·회차 등록·수정·종료), FR-01, D-03(삭제 없이 종료), D-06(JWT 로그인), D-18(검색), D-22(관리 API는 ADMIN), D-24, D-25(프록시·생성 클라이언트), D-35(관리 작업 잠금)
- 참고: 3회차 자료 6-1 "더 해 보기"(FR-03 관리 화면 초안), 다른 수강생 화면(`/admin/courses`, 상태 필터·과정 등록)

## 1. 현재 상태

- 백엔드 관리 API는 모두 있다: 과정 등록·수정·종료(`POST/PUT /api/v1/courses…`, `…/close`), 회차 추가·수정·종료(`POST /courses/{id}/sessions`, `PUT/POST /sessions/{id}…`), 로그인(`POST /api/v1/auth/login`)
- 생성 클라이언트에 `CourseApi`(관리 메서드 포함)와 `DefaultApi.login`이 있다. `Configuration.accessToken`으로 Bearer 토큰을 붙일 수 있다
- 프론트는 `course-mfe` 하나(임직원용 목록 화면). **라우터·로그인·토큰 처리 없음**
- 목록 API(`GET /api/v1/courses`)는 상태로 거르지 않고 **종료된 과정도 이미 돌려준다**. 관리 화면에서 "운영 중/종료"로 거르려면 상태 필터가 필요하다

## 2. 범위 (Q3)

| 포함 | 제외 |
|---|---|
| 관리자 로그인 (이메일·비밀번호 → JWT) | 회원가입, 로그아웃 이외 계정 관리 |
| 과정 목록: 제목·**상태** 필터, 페이지 | 임직원 신청 화면 (enrollment-mfe) |
| 과정 등록·수정·종료 | 수강생 명단·수료 처리 (FR-08) |
| 과정 상세의 회차 목록, 회차 추가·수정·종료 | Module Federation·shell (6주차) |

## 3. 화면 구조 (Q1)

`course-mfe` 안에 **상단 탭 두 개**를 둔다 (라우터 없이 상태로 전환).

```text
[과정 조회] [과정 관리]
 ├ 과정 조회: 지금의 CourseListPage (변경 없음)
 └ 과정 관리: 로그인 전 → AdminLoginForm
              로그인 후 → AdminCoursePage
                          ├ 필터: 과정명, 상태(전체/운영 중/종료), [과정 등록]
                          ├ 표: 과정명 · 분류 · 상태 칩 · 회차 수 · [수정] [종료]
                          └ 행 클릭 → 과정 상세 대화상자: 회차 표(번호·일정·장소·정원·남은 자리·상태) + [회차 추가] [수정] [종료]
```

- 등록·수정은 MUI `Dialog` 폼. 종료는 확인 대화상자(되돌릴 수 없음 안내, D-03)
- 종료된 과정·회차는 수정·종료 버튼을 비활성화 (서버도 409로 막음)
- 오류는 `ErrorResponse.message`를 `Alert`로 보여 준다 (예: C003 종료된 과정, C005 정원 < 확정 인원)

## 4. 인증 (Q2)

- `AdminLoginForm`이 `DefaultApi.login` 호출 → 토큰과 역할(`role` 클레임)을 받는다
- 토큰은 **`sessionStorage`**에 보관하고, 관리 API 호출용 `CourseApi`는 `Configuration({ basePath: '', accessToken: () => 토큰 })`로 만든다 (`src/api/` 한 곳에서만 생성 — frontend/CLAUDE.md)
- 역할이 `ADMIN`이 아니면 "관리자만 사용할 수 있습니다" 안내, 토큰 저장 안 함
- 401 응답(만료)이면 토큰을 지우고 로그인 화면으로
- `[로그아웃]` 버튼: 토큰 삭제
- 보안 메모: `sessionStorage`는 XSS에 노출된다. 운영 인증 방식(쿠키·BFF 등)은 MFE 연결(6주차)·보안 작업 때 다시 정한다 (결정 기록)

## 5. 백엔드 변경 (Q4)

- `GET /api/v1/courses`에 선택 파라미터 **`status`** (`OPEN` | `CLOSED`) 추가. 없으면 지금처럼 전체
  - `CourseSpecifications.search(keyword, category, status)`에 조건 추가, `CourseService`·`CourseController`·테스트 보강
  - 공개 조회 API이므로 임직원 화면에도 쓸 수 있다 (지금 임직원 화면은 전체를 보여 주고 상태 칩으로 구분)
- 그 밖의 백엔드 변경 없음. 스키마 변경 없음

## 6. 프론트 구조 (frontend/CLAUDE.md 규칙)

| 위치 | 내용 |
|---|---|
| `src/api/courseApi.ts` | 기존 공개 `courseApi` + 인증된 `adminCourseApi`, `authApi` (인스턴스는 여기서만) |
| `src/auth/adminSession.ts` | 토큰 저장·조회·삭제(sessionStorage), 역할 확인 |
| `src/hooks/useAdminCourses.ts` | 관리 목록 조회(keyword, status, page) — `useCourses`와 같은 모양 |
| `src/hooks/useCourseDetail.ts` | 과정 상세(회차 포함) 조회, 다시 불러오기 |
| `src/hooks/useCourseMutations.ts` | 등록·수정·종료, 회차 추가·수정·종료. `{ run, pending, error }` |
| `src/pages/AdminCoursePage.tsx` 등 | 화면은 훅만 쓰고 API를 직접 부르지 않는다 |
| `src/App.tsx` | 탭 전환. `main.tsx`는 `App`만 렌더링 |

- 페이지 번호는 0부터, 화면 표시만 +1 (frontend/CLAUDE.md)
- 새 의존성 없음 (MUI·생성 클라이언트만)

## 7. 테스트

| 종류 | 대상 |
|---|---|
| 백엔드 | `CourseServiceTest`·`CourseControllerTest`: `status=CLOSED`면 종료 과정만, `OPEN`이면 운영 중만, 없으면 전체, 잘못된 값이면 400 |
| 훅 (API mock) | `useAdminCourses`가 keyword·status·page를 그대로 전달, 실패 시 error / `useCourseMutations`가 성공 후 콜백, 실패 시 서버 메시지 |
| 인증 | `adminSession` 저장·삭제, ADMIN 아니면 거부 |
| 화면 (훅 mock) | 로그인 전 폼, 로그인 후 목록·상태 칩, 종료된 과정의 버튼 비활성, 등록 대화상자 제출, 오류 Alert |
| 직접 확인 | 백엔드(8080)·course-mfe(3001)를 띄워 관리자 로그인 → 등록 → 수정 → 회차 추가 → 종료, 상태 필터 |

## 8. 질문과 결정 (2026-11-16 개발자 승인: 모두 제안대로)

| # | 질문 | 결정 |
|---|---|---|
| Q1 | 관리 화면 위치 | **course-mfe 안 탭**(과정 조회 / 과정 관리). 라우터 의존성 없이 시작하고, 주소 기반 라우팅은 shell·MFE 연결(6주차) 때 |
| Q2 | 토큰 보관 | **sessionStorage** (새로 고침해도 유지, 탭을 닫으면 삭제). 보안 메모를 결정으로 남김 |
| Q3 | 범위 | **과정 + 회차 관리 전부** (API가 이미 있음). 너무 크면 회차 관리는 다음 PR로 분리 |
| Q4 | 상태 필터 | **목록 API에 `status` 파라미터 추가** (백엔드 소폭 변경) |

## 9. 결정 사항 기록 (승인 시)
- D-38: 관리 화면 구조·인증 보관 방식·범위·상태 필터 (Q1~Q4)
