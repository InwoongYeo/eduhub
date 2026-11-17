import { act, renderHook } from '@testing-library/react';
import { authApi } from '../api/courseApi';
import { getToken } from '../auth/adminSession';
import { useAdminLogin } from './useAdminLogin';

jest.mock('../api/courseApi', () => ({ authApi: { login: jest.fn() } }));
const login = authApi.login as jest.Mock;

const jwt = (role: string) => {
  const enc = (o: object) => btoa(JSON.stringify(o)).replace(/=+$/, '');
  return `${enc({ alg: 'HS256' })}.${enc({ role })}.sig`;
};

beforeEach(() => {
  login.mockReset();
  sessionStorage.clear();
});

test('ADMIN 토큰이면 저장하고 true', async () => {
  const token = jwt('ADMIN');
  login.mockResolvedValue({ data: { accessToken: token } });
  const { result } = renderHook(() => useAdminLogin());

  let ok: boolean | undefined;
  await act(async () => {
    ok = await result.current.login('admin@eduhub.local', 'pw');
  });

  expect(ok).toBe(true);
  expect(login).toHaveBeenCalledWith({ email: 'admin@eduhub.local', password: 'pw' });
  expect(getToken()).toBe(token);
  expect(result.current.error).toBeUndefined();
});

test('EMPLOYEE 토큰이면 저장하지 않고 오류', async () => {
  login.mockResolvedValue({ data: { accessToken: jwt('EMPLOYEE') } });
  const { result } = renderHook(() => useAdminLogin());

  let ok: boolean | undefined;
  await act(async () => {
    ok = await result.current.login('e@eduhub.local', 'pw');
  });

  expect(ok).toBe(false);
  expect(getToken()).toBeNull();
  expect(result.current.error).toBe('관리자만 사용할 수 있습니다');
});

test('401이면 서버 메시지를 오류로 돌려준다', async () => {
  login.mockRejectedValue({ response: { status: 401, data: { message: '이메일 또는 비밀번호가 올바르지 않습니다.' } } });
  const { result } = renderHook(() => useAdminLogin());

  let ok: boolean | undefined;
  await act(async () => {
    ok = await result.current.login('a@b.c', 'bad');
  });

  expect(ok).toBe(false);
  expect(getToken()).toBeNull();
  expect(result.current.error).toBe('이메일 또는 비밀번호가 올바르지 않습니다.');
});
