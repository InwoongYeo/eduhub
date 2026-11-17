import { fireEvent, render, screen } from '@testing-library/react';
import { App } from './App';
import { getToken, saveToken } from './auth/adminSession';

// 탭 전환만 검증한다. 각 화면은 자기 테스트가 따로 검증한다
jest.mock('./pages/CourseListPage', () => ({ CourseListPage: () => <div>조회 화면</div> }));
jest.mock('./pages/AdminLoginForm', () => ({ AdminLoginForm: () => <div>로그인 폼</div> }));
jest.mock('./pages/AdminCoursePage', () => ({ AdminCoursePage: () => <div>관리 화면</div> }));

beforeEach(() => sessionStorage.clear());

test('기본 탭은 과정 조회', () => {
  render(<App />);

  expect(screen.getByText('조회 화면')).toBeInTheDocument();
  expect(screen.queryByText('관리 화면')).not.toBeInTheDocument();
});

test('토큰이 없으면 과정 관리 탭에서 로그인 폼을 보여 준다', () => {
  render(<App />);

  fireEvent.click(screen.getByRole('tab', { name: '과정 관리' }));

  expect(screen.getByText('로그인 폼')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: '로그아웃' })).not.toBeInTheDocument();
});

test('토큰이 있으면 과정 관리 탭에서 관리 화면을 보여 준다', () => {
  saveToken('t');
  render(<App />);

  fireEvent.click(screen.getByRole('tab', { name: '과정 관리' }));

  expect(screen.getByText('관리 화면')).toBeInTheDocument();
});

test('로그아웃하면 토큰을 지우고 로그인 폼으로 돌아간다', () => {
  saveToken('t');
  render(<App />);
  fireEvent.click(screen.getByRole('tab', { name: '과정 관리' }));

  fireEvent.click(screen.getByRole('button', { name: '로그아웃' }));

  expect(getToken()).toBeNull();
  expect(screen.getByText('로그인 폼')).toBeInTheDocument();
});
