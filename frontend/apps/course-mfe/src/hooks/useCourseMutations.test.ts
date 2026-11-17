import { act, renderHook } from '@testing-library/react';
import { adminCourseApi } from '../api/courseApi';
import { getToken, saveToken } from '../auth/adminSession';
import { useCourseMutations } from './useCourseMutations';

jest.mock('../api/courseApi', () => ({
  adminCourseApi: {
    createCourse: jest.fn(),
    updateCourse: jest.fn(),
    closeCourse: jest.fn(),
    addSession: jest.fn(),
    updateSession: jest.fn(),
    closeSession: jest.fn(),
  },
}));
const api = adminCourseApi as unknown as Record<string, jest.Mock>;

beforeEach(() => {
  Object.values(api).forEach((m) => m.mockReset());
  sessionStorage.clear();
});

test('성공하면 true를 돌려주고 인자를 그대로 전달한다', async () => {
  api.createCourse.mockResolvedValue({ data: {} });
  api.closeSession.mockResolvedValue({ data: {} });
  const { result } = renderHook(() => useCourseMutations());

  let created: boolean | undefined;
  let closed: boolean | undefined;
  await act(async () => {
    created = await result.current.createCourse({ title: '자바', category: '개발' });
    closed = await result.current.closeSession(7);
  });

  expect(created).toBe(true);
  expect(closed).toBe(true);
  expect(api.createCourse).toHaveBeenCalledWith({ title: '자바', category: '개발' });
  expect(api.closeSession).toHaveBeenCalledWith(7);
  expect(result.current.error).toBeUndefined();
  expect(result.current.pending).toBe(false);
});

test('409이면 false와 서버 메시지', async () => {
  api.closeCourse.mockRejectedValue({ response: { status: 409, data: { message: '이미 종료된 과정입니다.' } } });
  const { result } = renderHook(() => useCourseMutations());

  let ok: boolean | undefined;
  await act(async () => {
    ok = await result.current.closeCourse(1);
  });

  expect(ok).toBe(false);
  expect(result.current.error).toBe('이미 종료된 과정입니다.');
  expect(result.current.unauthorized).toBe(false);
});

test('401이면 토큰을 지우고 unauthorized', async () => {
  saveToken('expired');
  api.updateCourse.mockRejectedValue({ response: { status: 401, data: { message: '인증 필요' } } });
  const { result } = renderHook(() => useCourseMutations());

  await act(async () => {
    await result.current.updateCourse(1, { title: 'a', category: '개발' });
  });

  expect(getToken()).toBeNull();
  expect(result.current.unauthorized).toBe(true);
});
