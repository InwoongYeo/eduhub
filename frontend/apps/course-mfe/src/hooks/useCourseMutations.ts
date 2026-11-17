import { useState } from 'react';
import type { CourseRequest, SessionRequest } from '@eduhub/api-client';
import { adminCourseApi } from '../api/courseApi';
import { isUnauthorized, messageOf } from '../api/apiError';
import { clearToken } from '../auth/adminSession';

/**
 * 과정·회차 등록/수정/종료 훅. 각 함수는 성공하면 true를 돌려준다.
 * pending·error는 하나를 공유한다 (대화상자 하나에서 한 번에 한 작업만 하므로).
 * 401이면 토큰을 지우고 unauthorized를 켠다.
 */
export function useCourseMutations() {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string>();
  const [unauthorized, setUnauthorized] = useState(false);

  async function run(action: () => Promise<unknown>): Promise<boolean> {
    setPending(true);
    setError(undefined);
    try {
      await action();
      return true;
    } catch (e) {
      if (isUnauthorized(e)) {
        clearToken();
        setUnauthorized(true);
      }
      setError(messageOf(e, '요청을 처리하지 못했습니다.'));
      return false;
    } finally {
      setPending(false);
    }
  }

  return {
    pending,
    error,
    unauthorized,
    clearError: () => setError(undefined),
    createCourse: (body: CourseRequest) => run(() => adminCourseApi.createCourse(body)),
    updateCourse: (courseId: number, body: CourseRequest) => run(() => adminCourseApi.updateCourse(courseId, body)),
    closeCourse: (courseId: number) => run(() => adminCourseApi.closeCourse(courseId)),
    addSession: (courseId: number, body: SessionRequest) => run(() => adminCourseApi.addSession(courseId, body)),
    updateSession: (sessionId: number, body: SessionRequest) => run(() => adminCourseApi.updateSession(sessionId, body)),
    closeSession: (sessionId: number) => run(() => adminCourseApi.closeSession(sessionId)),
  };
}
