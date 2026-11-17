# 구현계획: FR-03 과정 관리 화면

- 설계명세: `docs/design/admin-ui.md` (2026-11-16 승인). **계획과 설계가 다르면 설계를 따르고 보고한다**
- 브랜치: `feature/fr-03-admin-ui`
- 상태: **승인** (2026-11-16)
- 공통: 루트·`backend/CLAUDE.md`·`frontend/CLAUDE.md`, `.claude/skills/eduhub-api/SKILL.md`(백엔드). 백엔드 명령은 `backend/`, 프론트는 `frontend/`에서

| 작업 | 담당 | 완료 명령 |
|---|---|---|
| T1 백엔드 `status` 필터 | implementer → reviewer | `gradlew test --tests "*CourseServiceTest" --tests "*CourseControllerTest" --tests "*SchemaMappingTest"`, `spotlessCheck` |
| T2 API 스펙 갱신 | 메인 (백엔드 실행) | `npm run refresh:spec -w packages/api-client`, `npm run generate:api` |
| T3 프론트: 인증·API·훅 | implementer → reviewer | `npm test -w apps/course-mfe`, `npm run typecheck -w apps/course-mfe` |
| T4 프론트: 탭·로그인·관리 화면·대화상자 | implementer → reviewer | 같음 |
| T5 실제 화면 확인 | 메인 + **개발자** | 백엔드 8080·course-mfe 3001에서 로그인 → 등록 → 수정 → 회차 추가 → 종료, 상태 필터 |

## T1. 백엔드 `status` 필터

| 파일 | 변경 |
|---|---|
| `course/repository/CourseSpecifications.java` | `search(String keyword, String category, CourseStatus status)` — `status`가 null이 아니면 `cb.equal(root.get("status"), status)` |
| `course/service/CourseService.java` | `getCourses(keyword, category, status, pageable)`로 전달 |
| `course/controller/CourseController.java` | `@RequestParam(required = false) CourseStatus status` 추가, `@Operation` 설명에 "status(OPEN·CLOSED, 생략하면 전체)". 잘못된 값은 기존 `MethodArgumentTypeMismatchException` → 400 G001 |

**테스트**
- `CourseServiceTest`: status를 Specification으로 넘기는지(기존 방식에 맞춰)
- `CourseControllerTest`: 과정 하나를 관리 API로 종료한 뒤 `status=CLOSED` → 그 과정만, `status=OPEN` → 종료 과정 제외, 생략 → 둘 다, `status=WRONG` → 400 G001

## T2. 스펙 (메인)
- 백엔드 실행 → `refresh:spec` → `generate:api`. `getCourses`에 `status` 인자가 생긴다. 커밋은 `openapi.json`만

## T3. 프론트: 인증·API·훅 (`frontend/apps/course-mfe/src`)

| 파일 | 내용 |
|---|---|
| `auth/adminSession.ts` (신규) | `getToken()`, `saveToken(token)`, `clearToken()` — `sessionStorage` 키 `eduhub.adminToken`. `roleOf(token)`: JWT payload의 `role` 클레임 (base64url 디코드, 실패 시 null). try/catch로 저장소 오류에 견딤 |
| `api/courseApi.ts` | 기존 `courseApi` 유지. 추가: `authApi = new DefaultApi(new Configuration({ basePath: '' }))`, `adminCourseApi = new CourseApi(new Configuration({ basePath: '', accessToken: () => getToken() ?? '' }))` — 인스턴스는 이 파일에서만 |
| `hooks/useAdminLogin.ts` (신규) | `login(email, password)`: `authApi.login` → 토큰 역할이 ADMIN이면 저장하고 true, 아니면 저장하지 않고 "관리자만 사용할 수 있습니다" 오류. 실패 시 서버 `message`. `{ login, pending, error }` |
| `hooks/useAdminCourses.ts` (신규) | `{ keyword, status, page, size, reloadKey }` → `adminCourseApi.getCourses(keyword, undefined, page, size, undefined, status)` (생성된 인자 순서 확인). 출력 `{ data, loading, error }`, 늦게 온 응답 무시(`useCourses`와 같은 방식) |
| `hooks/useCourseDetail.ts` (신규) | `courseId`가 있으면 `adminCourseApi.getCourse`. `{ data, loading, error, reload }` |
| `hooks/useCourseMutations.ts` (신규) | `createCourse`, `updateCourse`, `closeCourse`, `addSession`, `updateSession`, `closeSession`. 각 호출은 `{ run(args): Promise<boolean>, pending, error }` 형태 또는 하나의 훅에서 `pending·error` 공유. 실패 시 `ErrorResponse.message`를 error로, **401이면 `clearToken()`** 후 `unauthorized` 플래그 |

**테스트 (API mock, `useCourses.test.ts` 방식)**
- `adminSession.test.ts`: 저장·조회·삭제, `roleOf`(ADMIN, EMPLOYEE, 깨진 토큰 → null)
- `useAdminLogin.test.ts`: ADMIN 토큰 → 저장·true, EMPLOYEE 토큰 → 저장 안 함·오류, 401 → 서버 메시지
- `useAdminCourses.test.ts`: keyword·status·page를 그대로 전달, 실패 시 error
- `useCourseMutations.test.ts`: 성공 true, 409 → 서버 메시지, 401 → 토큰 삭제

## T4. 프론트: 화면

| 파일 | 내용 |
|---|---|
| `App.tsx` (신규) | MUI `Tabs`: [과정 조회] → 기존 `CourseListPage`, [과정 관리] → 토큰 없으면 `AdminLoginForm`, 있으면 `AdminCoursePage`(+ [로그아웃]) |
| `main.tsx` | `App`만 렌더링 (제목 "교육과정"은 각 페이지 안으로) |
| `pages/AdminLoginForm.tsx` | 이메일·비밀번호·[로그인], 오류 Alert. `useAdminLogin`만 사용 |
| `pages/AdminCoursePage.tsx` | 제목 "과정 관리", 과정명 입력·상태 select(전체/운영 중/종료)·[과정 등록], 표(과정명·분류·상태 칩·회차 수·[수정]·[종료]), 페이지(0부터, 표시만 +1), 행 클릭 → 상세 대화상자. 종료된 과정은 수정·종료 비활성 |
| `components/CourseFormDialog.tsx` | 제목·설명·분류(분류는 `CourseListPage`의 `CATEGORIES`를 공유 상수로 옮겨 재사용) 등록·수정 |
| `components/SessionFormDialog.tsx` | 시작·종료 일시(`datetime-local`), 장소, 정원 등록·수정 |
| `components/CourseDetailDialog.tsx` | 회차 표(번호·일정·장소·정원·남은 자리·상태 칩·[수정]·[종료]), [회차 추가] |
| `components/ConfirmDialog.tsx` | 종료 확인 ("되돌릴 수 없습니다") |

- 성공하면 목록·상세를 다시 불러온다(`reloadKey`·`reload`)
- 오류는 대화상자 안 `Alert`에 서버 메시지
- 401(`unauthorized`)이면 로그인 화면으로

**테스트 (훅 mock)**
- `App.test.tsx`: 기본 탭은 과정 조회, 관리 탭 + 토큰 없음 → 로그인 폼, 토큰 있음 → 관리 화면
- `AdminCoursePage.test.tsx`: 목록·상태 칩, 종료 과정의 버튼 비활성, 상태 select 변경 시 status 전달·첫 페이지, [과정 등록] → 대화상자 제출 시 `createCourse` 호출, 오류 Alert
- 기존 `CourseListPage`·`useCourses` 테스트 유지 (분류 상수 이동에 맞춰 import만)

## T5. 실제 화면 확인
- 백엔드(8080)·course-mfe(3001) 실행, 브라우저 창에서 `admin@eduhub.local`(local 초기 계정)로 로그인 → 과정 등록 → 수정 → 회차 추가 → 회차 종료 → 과정 종료 → 상태 필터. 임직원 계정으로 로그인하면 거부
- 메인이 브라우저 창으로 확인하고 스크린샷을 남긴 뒤, 개발자가 직접 확인

## 위험 영역
| 위험 | 확인 |
|---|---|
| 화면이 API를 직접 부름 | `pages/`·`components/`에 `courseApi`/`adminCourseApi` import가 없는지 |
| API 인스턴스가 여러 곳에서 생성 | `new CourseApi`·`new DefaultApi`가 `api/courseApi.ts`에만 |
| 토큰 노출 | 토큰을 콘솔·URL에 남기지 않는지, 로그아웃·401에서 삭제되는지 |
| 페이지 번호 | API 0부터, 표시만 +1 |
| 공개 화면 회귀 | 기존 `CourseListPage` 테스트 통과, 분류 상수 이동 |

## 규모
백엔드 3 + 테스트, 프론트 신규 약 12 + 테스트. 여러 계층 → 개발자 확인 후 진행 (CLAUDE.md 개발 진행 절차 5번). 커밋은 백엔드(T1·T2)와 프론트(T3·T4)를 나눈다
