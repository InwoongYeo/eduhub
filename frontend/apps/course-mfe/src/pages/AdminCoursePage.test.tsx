import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { useAdminCourses } from '../hooks/useAdminCourses';
import { useCourseDetail } from '../hooks/useCourseDetail';
import { useCourseMutations } from '../hooks/useCourseMutations';
import { AdminCoursePage } from './AdminCoursePage';

// 화면 테스트는 훅을 mock한다. API 호출 방식은 각 훅 테스트가 검증한다
jest.mock('../hooks/useAdminCourses');
jest.mock('../hooks/useCourseDetail');
jest.mock('../hooks/useCourseMutations');
const mockList = useAdminCourses as jest.Mock;
const mockDetail = useCourseDetail as jest.Mock;
const mockMutations = useCourseMutations as jest.Mock;

const page = (content: object[]) => ({
  data: { content, page: 0, size: 10, totalElements: content.length, totalPages: 1 },
  loading: false,
  error: undefined,
  unauthorized: false,
});

const OPEN = { id: 1, title: '자바 기초', category: '개발', status: 'OPEN', sessionCount: 2 };
const CLOSED = { id: 2, title: '오피스 활용', category: '직무공통', status: 'CLOSED', sessionCount: 1 };

let mutations: Record<string, jest.Mock | unknown>;
const onUnauthorized = jest.fn();

beforeEach(() => {
  mockList.mockReset().mockReturnValue(page([OPEN, CLOSED]));
  mockDetail.mockReset().mockReturnValue({ data: undefined, loading: false, error: undefined, unauthorized: false, reload: jest.fn() });
  mutations = {
    pending: false,
    error: undefined,
    unauthorized: false,
    clearError: jest.fn(),
    createCourse: jest.fn().mockResolvedValue(true),
    updateCourse: jest.fn().mockResolvedValue(true),
    closeCourse: jest.fn().mockResolvedValue(true),
    addSession: jest.fn().mockResolvedValue(true),
    updateSession: jest.fn().mockResolvedValue(true),
    closeSession: jest.fn().mockResolvedValue(true),
  };
  mockMutations.mockReset().mockImplementation(() => mutations);
  onUnauthorized.mockReset();
});

test('과정 목록과 상태 칩을 보여 준다', () => {
  render(<AdminCoursePage onUnauthorized={onUnauthorized} />);

  const rows = screen.getAllByRole('row');
  expect(within(rows[1]).getByText('자바 기초')).toBeInTheDocument();
  expect(within(rows[1]).getByText('운영중')).toBeInTheDocument();
  expect(within(rows[2]).getAllByText('종료')).toHaveLength(2); // 상태 칩 + 종료 버튼
});

test('종료된 과정은 수정·종료 버튼이 비활성화된다', () => {
  render(<AdminCoursePage onUnauthorized={onUnauthorized} />);

  const rows = screen.getAllByRole('row');
  expect(within(rows[1]).getByRole('button', { name: '수정' })).toBeEnabled();
  expect(within(rows[2]).getByRole('button', { name: '수정' })).toBeDisabled();
  expect(within(rows[2]).getByRole('button', { name: '종료' })).toBeDisabled();
});

test('상태를 선택하면 그 상태로 첫 페이지를 조회한다', () => {
  render(<AdminCoursePage onUnauthorized={onUnauthorized} />);

  fireEvent.mouseDown(screen.getByRole('combobox', { name: '상태' }));
  fireEvent.click(screen.getByRole('option', { name: '종료' }));

  expect(mockList).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'CLOSED', page: 0 }));
});

test('과정 등록 대화상자를 제출하면 createCourse를 호출하고 목록을 다시 불러온다', async () => {
  render(<AdminCoursePage onUnauthorized={onUnauthorized} />);

  fireEvent.click(screen.getByRole('button', { name: '과정 등록' }));
  const dialog = screen.getByRole('dialog');
  fireEvent.change(within(dialog).getByLabelText(/제목/), { target: { value: '새 과정' } });
  fireEvent.mouseDown(within(dialog).getByRole('combobox', { name: /분류/ }));
  fireEvent.click(screen.getByRole('option', { name: '데이터' }));
  fireEvent.click(within(dialog).getByRole('button', { name: '저장' }));

  await waitFor(() => expect(mutations.createCourse).toHaveBeenCalledWith({
    title: '새 과정', description: undefined, category: '데이터',
  }));
  await waitFor(() => expect(mockList).toHaveBeenLastCalledWith(expect.objectContaining({ reloadKey: 1 })));
});

test('과정 종료는 확인 후 closeCourse를 호출한다', async () => {
  render(<AdminCoursePage onUnauthorized={onUnauthorized} />);

  fireEvent.click(within(screen.getAllByRole('row')[1]).getByRole('button', { name: '종료' }));
  const dialog = screen.getByRole('dialog');
  expect(dialog).toHaveTextContent('되돌릴 수 없습니다');
  fireEvent.click(within(dialog).getByRole('button', { name: '종료' }));

  await waitFor(() => expect(mutations.closeCourse).toHaveBeenCalledWith(1));
});

test('서버 오류 메시지를 대화상자의 Alert로 보여 준다', () => {
  mutations.error = 'C003 이미 종료된 과정입니다.';
  render(<AdminCoursePage onUnauthorized={onUnauthorized} />);

  fireEvent.click(screen.getByRole('button', { name: '과정 등록' }));

  expect(within(screen.getByRole('dialog')).getByRole('alert')).toHaveTextContent('C003 이미 종료된 과정입니다.');
});

test('목록을 불러오지 못하면 오류 Alert를 보여 준다', () => {
  mockList.mockReturnValue({ ...page([]), error: '서버 오류' });

  render(<AdminCoursePage onUnauthorized={onUnauthorized} />);

  expect(screen.getByRole('alert')).toHaveTextContent('서버 오류');
});

test('401이면 onUnauthorized를 호출한다', () => {
  mockList.mockReturnValue({ ...page([]), unauthorized: true });

  render(<AdminCoursePage onUnauthorized={onUnauthorized} />);

  expect(onUnauthorized).toHaveBeenCalled();
});

test('행을 클릭하면 회차 표가 있는 상세 대화상자를 연다', () => {
  mockDetail.mockReturnValue({
    data: {
      id: 1, title: '자바 기초', description: '설명', category: '개발', status: 'OPEN',
      sessions: [{
        id: 10, sessionNo: 1, startsAt: '2026-12-01T09:00:00', endsAt: '2026-12-01T18:00:00',
        location: '본관 3층', capacity: 20, remainingSeats: 5, status: 'OPEN',
      }],
    },
    loading: false, error: undefined, unauthorized: false, reload: jest.fn(),
  });
  render(<AdminCoursePage onUnauthorized={onUnauthorized} />);

  fireEvent.click(screen.getByText('자바 기초'));

  const dialog = screen.getByRole('dialog');
  expect(within(dialog).getByText('본관 3층')).toBeInTheDocument();
  expect(within(dialog).getByRole('button', { name: '회차 추가' })).toBeEnabled();
  expect(mockDetail).toHaveBeenLastCalledWith(1);
});

test('수정 버튼 후 상세 조회가 실패하면 오류가 보이고 닫을 수 있다', () => {
  mockDetail.mockReturnValue({ data: undefined, loading: false, error: '과정을 찾을 수 없습니다.', unauthorized: false, reload: jest.fn() });
  render(<AdminCoursePage onUnauthorized={onUnauthorized} />);

  fireEvent.click(within(screen.getAllByRole('row')[1]).getByRole('button', { name: '수정' }));

  const dialog = screen.getByRole('dialog');
  expect(within(dialog).getByRole('alert')).toHaveTextContent('과정을 찾을 수 없습니다.');
  fireEvent.click(within(dialog).getByRole('button', { name: '닫기' }));
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
});

test('수정 버튼 후 상세 조회 중에는 로딩 표시를 보여 준다', () => {
  mockDetail.mockReturnValue({ data: undefined, loading: true, error: undefined, unauthorized: false, reload: jest.fn() });
  render(<AdminCoursePage onUnauthorized={onUnauthorized} />);

  fireEvent.click(within(screen.getAllByRole('row')[1]).getByRole('button', { name: '수정' }));

  expect(within(screen.getByRole('dialog')).getByRole('progressbar')).toBeInTheDocument();
});
