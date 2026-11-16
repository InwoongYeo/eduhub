import { fireEvent, render, screen, within } from '@testing-library/react';
import { useCourses } from '../hooks/useCourses';
import { CourseListPage } from './CourseListPage';

// 화면 테스트는 훅을 mock한다. API 호출 방식은 useCourses.test.ts가 따로 검증한다
jest.mock('../hooks/useCourses');
const mockUseCourses = useCourses as jest.Mock;

const page = (content: object[]) => ({
  data: { content, page: 0, size: 10, totalElements: content.length, totalPages: 1 },
  loading: false,
  error: undefined,
});

beforeEach(() => mockUseCourses.mockReset());

test('과정 목록을 표로 보여 준다', () => {
  mockUseCourses.mockReturnValue(page([
    { id: 1, title: '자바 기초', category: '개발', status: 'OPEN', sessionCount: 2 },
  ]));

  render(<CourseListPage />);

  expect(screen.getByText('자바 기초')).toBeInTheDocument();
  expect(screen.getByRole('cell', { name: '개발' })).toBeInTheDocument();
});

test('과정 상태를 운영중·종료로 보여 준다', () => {
  mockUseCourses.mockReturnValue(page([
    { id: 1, title: '자바 기초', category: '개발', status: 'OPEN', sessionCount: 2 },
    { id: 2, title: '오피스 활용', category: '직무공통', status: 'CLOSED', sessionCount: 1 },
  ]));

  render(<CourseListPage />);

  const rows = screen.getAllByRole('row');
  expect(within(rows[1]).getByText('운영중')).toBeInTheDocument();
  expect(within(rows[2]).getByText('종료')).toBeInTheDocument();
});

test('분류를 선택하면 그 분류로 첫 페이지를 조회한다', () => {
  mockUseCourses.mockReturnValue(page([]));

  render(<CourseListPage />);
  fireEvent.mouseDown(screen.getByRole('combobox', { name: '분류' }));
  fireEvent.click(screen.getByRole('option', { name: '리더십' }));

  expect(mockUseCourses).toHaveBeenLastCalledWith(
    expect.objectContaining({ category: '리더십', page: 0 }),
  );
});

test('불러오기에 실패하면 에러 메시지를 보여 준다', () => {
  mockUseCourses.mockReturnValue({ data: undefined, loading: false, error: new Error('fail') });

  render(<CourseListPage />);

  expect(screen.getByRole('alert')).toHaveTextContent('과정 목록을 불러오지 못했습니다.');
});
