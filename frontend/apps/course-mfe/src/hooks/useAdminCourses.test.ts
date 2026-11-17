import { renderHook, waitFor } from '@testing-library/react';
import { adminCourseApi } from '../api/courseApi';
import { getToken, saveToken } from '../auth/adminSession';
import { useAdminCourses } from './useAdminCourses';

jest.mock('../api/courseApi', () => ({ adminCourseApi: { getCourses: jest.fn() } }));
const getCourses = adminCourseApi.getCourses as jest.Mock;

const pageOf = (titles: string[]) => ({
  data: {
    content: titles.map((title, i) => ({ id: i + 1, title, category: '개발', status: 'OPEN', sessionCount: 1 })),
    page: 0,
    size: 10,
    totalElements: titles.length,
    totalPages: 1,
  },
});

beforeEach(() => {
  getCourses.mockReset();
  sessionStorage.clear();
});

test('keyword·status·page를 생성된 인자 순서(keyword, category, status, page, size)로 전달한다', async () => {
  getCourses.mockResolvedValue(pageOf(['자바 기초']));

  const { result } = renderHook(() => useAdminCourses({ keyword: '자바', status: 'CLOSED', page: 2, size: 10 }));

  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(getCourses).toHaveBeenCalledWith('자바', undefined, 'CLOSED', 2, 10);
  expect(result.current.data?.content[0].title).toBe('자바 기초');
});

test('빈 keyword·status는 undefined로 보낸다 (전체)', async () => {
  getCourses.mockResolvedValue(pageOf([]));

  const { result } = renderHook(() => useAdminCourses({ keyword: '', status: undefined, page: 0, size: 10 }));

  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(getCourses).toHaveBeenCalledWith(undefined, undefined, undefined, 0, 10);
});

test('reloadKey가 바뀌면 다시 불러온다', async () => {
  getCourses.mockResolvedValue(pageOf([]));

  const { result, rerender } = renderHook(
    ({ reloadKey }) => useAdminCourses({ page: 0, size: 10, reloadKey }),
    { initialProps: { reloadKey: 0 } },
  );
  await waitFor(() => expect(result.current.loading).toBe(false));
  rerender({ reloadKey: 1 });

  await waitFor(() => expect(getCourses).toHaveBeenCalledTimes(2));
});

test('실패하면 서버 메시지를 error로 돌려준다', async () => {
  getCourses.mockRejectedValue({ response: { status: 500, data: { message: '서버 오류' } } });

  const { result } = renderHook(() => useAdminCourses({ page: 0, size: 10 }));

  await waitFor(() => expect(result.current.loading).toBe(false));
  expect(result.current.error).toBe('서버 오류');
  expect(result.current.unauthorized).toBe(false);
  expect(result.current.data).toBeUndefined();
});

test('401이면 토큰을 지우고 unauthorized', async () => {
  saveToken('expired');
  getCourses.mockRejectedValue({ response: { status: 401, data: { message: '만료' } } });

  const { result } = renderHook(() => useAdminCourses({ page: 0, size: 10 }));

  await waitFor(() => expect(result.current.unauthorized).toBe(true));
  expect(getToken()).toBeNull();
});
