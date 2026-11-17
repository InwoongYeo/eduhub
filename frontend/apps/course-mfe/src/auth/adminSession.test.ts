import { clearToken, getToken, roleOf, saveToken } from './adminSession';

/** 서명 없는 테스트용 JWT (base64url) */
function fakeJwt(claims: object): string {
  const encode = (o: object) => btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${encode({ alg: 'HS256' })}.${encode(claims)}.sig`;
}

beforeEach(() => sessionStorage.clear());

test('토큰을 저장·조회·삭제한다', () => {
  expect(getToken()).toBeNull();

  saveToken('abc');
  expect(getToken()).toBe('abc');

  clearToken();
  expect(getToken()).toBeNull();
});

test('저장소 접근이 실패해도 예외를 던지지 않는다', () => {
  const spy = jest.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
    throw new Error('denied');
  });

  expect(getToken()).toBeNull();
  spy.mockRestore();
});

test('roleOf는 role 클레임을 돌려준다', () => {
  expect(roleOf(fakeJwt({ sub: '1', role: 'ADMIN' }))).toBe('ADMIN');
  expect(roleOf(fakeJwt({ sub: '1', role: 'EMPLOYEE' }))).toBe('EMPLOYEE');
});

test('base64url 문자(-, _)와 패딩 생략도 해석한다', () => {
  // '>>>???' 같은 값은 표준 base64에서 +, /가 나온다
  expect(roleOf(fakeJwt({ role: 'ADMIN', memo: '>>>???~~~' }))).toBe('ADMIN');
});

test('깨진 토큰이거나 role이 없으면 null', () => {
  expect(roleOf('not-a-jwt')).toBeNull();
  expect(roleOf('a.@@@.c')).toBeNull();
  expect(roleOf(fakeJwt({ sub: '1' }))).toBeNull();
});
