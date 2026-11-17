// 관리자 토큰 보관 (D-38). sessionStorage는 XSS에 노출되므로 운영 인증 방식은 6주차에 다시 정한다.
// 토큰은 콘솔·URL에 남기지 않는다.
const KEY = 'eduhub.adminToken';

export function getToken(): string | null {
  try {
    return sessionStorage.getItem(KEY);
  } catch {
    return null; // 저장소 접근이 막힌 환경
  }
}

export function saveToken(token: string): void {
  try {
    sessionStorage.setItem(KEY, token);
  } catch {
    // 저장하지 못하면 로그인 상태가 유지되지 않을 뿐이다
  }
}

export function clearToken(): void {
  try {
    sessionStorage.removeItem(KEY);
  } catch {
    // 무시
  }
}

/** JWT payload의 role 클레임. 형식이 깨졌거나 클레임이 없으면 null. (서명 검증은 서버가 한다) */
export function roleOf(token: string): string | null {
  try {
    const payload = token.split('.')[1];
    if (!payload) return null;
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
    const claims: unknown = JSON.parse(atob(padded));
    const role = (claims as { role?: unknown } | null)?.role;
    return typeof role === 'string' ? role : null;
  } catch {
    return null;
  }
}
