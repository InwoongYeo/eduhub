import { act, renderHook, waitFor } from '@testing-library/react';
import { adminCourseApi } from '../api/courseApi';
import { getToken, saveToken } from '../auth/adminSession';
import { useCourseDetail } from './useCourseDetail';

jest.mock('../api/courseApi', () => ({ adminCourseApi: { getCourse: jest.fn() } }));
const getCourse = adminCourseApi.getCourse as jest.Mock;

const detailOf = (id: number) => ({ data: { id, title: `과정${id}`, category: '개발', status: 'OPEN', sessions: [] } });

beforeEach(() => {
  getCourse.mockReset();
  sessionStorage.clear();
});

test('courseId가 없으면 조회하지 않는다', () => {
  const { result } = renderHook(() => useCourseDetail(undefined));

  expect(getCourse).not.toHaveBeenCalled();
  expect(result.current.data).toBeUndefined();
  expect(result.current.loading).toBe(false);
});

test('조회에 성공하면 data를 돌려준다', async () => {
  getCourse.mockResolvedValue(detailOf(1));

  const { result } = renderHook(() => useCourseDetail(1));

  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(getCourse).toHaveBeenCalledWith(1);
  expect(result.current.data?.id).toBe(1);
});

test('reload하면 다시 조회한다', async () => {
  getCourse.mockResolvedValue(detailOf(1));
  const { result } = renderHook(() => useCourseDetail(1));
  await waitFor(() => expect(result.current.loading).toBe(false));

  act(() => result.current.reload());

  await waitFor(() => expect(getCourse).toHaveBeenCalledTimes(2));
});

test('courseId가 바뀌면 이전 응답은 무시한다', async () => {
  let resolveFirst: (v: unknown) => void = () => {};
  getCourse.mockImplementationOnce(() => new Promise((r) => { resolveFirst = r; }));
  getCourse.mockResolvedValueOnce(detailOf(2));

  const { result, rerender } = renderHook(({ id }) => useCourseDetail(id), { initialProps: { id: 1 } });
  rerender({ id: 2 });
  await waitFor(() => expect(result.current.data?.id).toBe(2));
  await act(async () => resolveFirst(detailOf(1)));

  expect(result.current.data?.id).toBe(2);
});

test('401이면 토큰을 지우고 unauthorized', async () => {
  saveToken('expired');
  getCourse.mockRejectedValue({ response: { status: 401, data: { message: '만료' } } });

  const { result } = renderHook(() => useCourseDetail(1));

  await waitFor(() => expect(result.current.unauthorized).toBe(true));
  expect(getToken()).toBeNull();
});

test('실패하면 서버 메시지를 error로 돌려준다', async () => {
  getCourse.mockRejectedValue({ response: { status: 404, data: { message: '과정을 찾을 수 없습니다.' } } });

  const { result } = renderHook(() => useCourseDetail(1));

  await waitFor(() => expect(result.current.error).toBe('과정을 찾을 수 없습니다.'));
  expect(result.current.unauthorized).toBe(false);
});
