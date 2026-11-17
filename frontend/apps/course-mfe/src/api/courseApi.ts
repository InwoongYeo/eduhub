import { Configuration, CourseApi, DefaultApi } from '@eduhub/api-client';
import { getToken } from '../auth/adminSession';

// 생성된 클라이언트의 인스턴스는 여기 한 곳에서만 만든다 (Axios를 직접 생성하지 않는다)
// basePath '': 같은 출처로 호출하고 개발 서버 프록시가 백엔드(8080)로 넘긴다 (vite.config.ts)
export const courseApi = new CourseApi(new Configuration({ basePath: '' }));

/** 로그인 (D-06). 토큰이 필요 없다 */
export const authApi = new DefaultApi(new Configuration({ basePath: '' }));

/** 관리 API (D-22: ADMIN). 호출 때마다 sessionStorage의 토큰을 Bearer로 붙인다 */
export const adminCourseApi = new CourseApi(
  new Configuration({ basePath: '', accessToken: () => getToken() ?? '' }),
);
