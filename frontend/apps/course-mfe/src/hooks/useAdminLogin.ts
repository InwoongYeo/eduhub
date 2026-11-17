import { useState } from 'react';
import { authApi } from '../api/courseApi';
import { messageOf } from '../api/apiError';
import { roleOf, saveToken } from '../auth/adminSession';

/** 관리자 로그인 훅. ADMIN 역할일 때만 토큰을 저장한다 (D-38). */
export function useAdminLogin() {
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string>();

  /** 성공하면 true. 실패 사유는 error에 담긴다. */
  async function login(email: string, password: string): Promise<boolean> {
    setPending(true);
    setError(undefined);
    try {
      const response = await authApi.login({ email, password });
      const token = response.data.accessToken;
      if (!token || roleOf(token) !== 'ADMIN') {
        setError('관리자만 사용할 수 있습니다');
        return false;
      }
      saveToken(token);
      return true;
    } catch (e) {
      setError(messageOf(e, '로그인하지 못했습니다.'));
      return false;
    } finally {
      setPending(false);
    }
  }

  return { login, pending, error };
}
