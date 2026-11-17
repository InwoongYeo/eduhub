// API 오류(AxiosError)에서 상태 코드와 서버 메시지(ErrorResponse.message)를 꺼낸다.
// axios를 직접 import하지 않고 모양만 확인한다.
interface ErrorLike {
  response?: { status?: number; data?: { message?: unknown } };
}

export function statusOf(e: unknown): number | undefined {
  return (e as ErrorLike | undefined)?.response?.status;
}

export function isUnauthorized(e: unknown): boolean {
  return statusOf(e) === 401;
}

export function messageOf(e: unknown, fallback: string): string {
  const message = (e as ErrorLike | undefined)?.response?.data?.message;
  return typeof message === 'string' && message ? message : fallback;
}
