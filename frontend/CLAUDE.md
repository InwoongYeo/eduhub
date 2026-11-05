# frontend

- UI 컴포넌트는 MUI를 사용한다.
- API 호출은 공통 Axios 클라이언트 하나를 거친다. 컴포넌트에서 axios를 직접 부르지 않는다.
- 백엔드 오류 응답 형식은 `{code, message, timestamp}`다 (`backend` `ErrorResponse`).
