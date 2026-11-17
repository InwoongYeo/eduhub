import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type { CourseDetailResponse } from '@eduhub/api-client';
import { useCourseMutations } from '../hooks/useCourseMutations';
import { CourseDetailDialog } from './CourseDetailDialog';

jest.mock('../hooks/useCourseMutations');
const mockMutations = useCourseMutations as jest.Mock;

const OPEN_SESSION = {
  id: 10, sessionNo: 1, startsAt: '2026-12-01T09:00:00', endsAt: '2026-12-01T18:30:45',
  location: '본관 3층', capacity: 20, remainingSeats: 5, status: 'OPEN',
};
const CLOSED_SESSION = {
  id: 11, sessionNo: 2, startsAt: '2026-11-01T09:00:00', endsAt: '2026-11-01T18:00:00',
  location: '별관', capacity: 10, remainingSeats: 0, status: 'CLOSED',
};

const detail = (status: string): CourseDetailResponse => ({
  id: 1, title: '자바 기초', description: '설명', category: '개발', status,
  sessions: [OPEN_SESSION, CLOSED_SESSION],
} as unknown as CourseDetailResponse);

let mutations: Record<string, jest.Mock | unknown>;
const onUnauthorized = jest.fn();
const onChanged = jest.fn();

const renderDialog = (status = 'OPEN') => render(
  <CourseDetailDialog
    detail={detail(status)}
    loading={false}
    onChanged={onChanged}
    onUnauthorized={onUnauthorized}
    onClose={jest.fn()}
  />,
);

beforeEach(() => {
  mutations = {
    pending: false,
    error: undefined,
    unauthorized: false,
    clearError: jest.fn(),
    addSession: jest.fn().mockResolvedValue(true),
    updateSession: jest.fn().mockResolvedValue(true),
    closeSession: jest.fn().mockResolvedValue(true),
  };
  mockMutations.mockReset().mockImplementation(() => mutations);
  onUnauthorized.mockReset();
  onChanged.mockReset();
});

test('종료된 회차는 수정·종료가 비활성이고, 과정이 종료면 회차 추가와 모든 회차 버튼이 비활성이다', () => {
  const { unmount } = renderDialog('OPEN');
  let rows = screen.getAllByRole('row');
  expect(within(rows[1]).getByRole('button', { name: '수정' })).toBeEnabled();
  expect(within(rows[1]).getByRole('button', { name: '종료' })).toBeEnabled();
  expect(within(rows[2]).getByRole('button', { name: '수정' })).toBeDisabled();
  expect(within(rows[2]).getByRole('button', { name: '종료' })).toBeDisabled();
  expect(screen.getByRole('button', { name: '회차 추가' })).toBeEnabled();
  unmount();

  renderDialog('CLOSED');
  rows = screen.getAllByRole('row');
  expect(screen.getByRole('button', { name: '회차 추가' })).toBeDisabled();
  expect(within(rows[1]).getByRole('button', { name: '수정' })).toBeDisabled();
  expect(within(rows[1]).getByRole('button', { name: '종료' })).toBeDisabled();
});

test('회차 수정 저장은 그 회차의 sessionId로 updateSession을 호출한다', async () => {
  renderDialog();

  fireEvent.click(within(screen.getAllByRole('row')[1]).getByRole('button', { name: '수정' }));
  const form = screen.getByRole('dialog', { name: '회차 수정' });
  expect(within(form).getByLabelText(/시작 일시/)).toHaveValue('2026-12-01T09:00');
  expect(within(form).getByLabelText(/종료 일시/)).toHaveValue('2026-12-01T18:30');
  fireEvent.change(within(form).getByLabelText(/장소/), { target: { value: '본관 5층' } });
  fireEvent.click(within(form).getByRole('button', { name: '저장' }));

  await waitFor(() => expect(mutations.updateSession).toHaveBeenCalledWith(10, {
    startsAt: '2026-12-01T09:00', endsAt: '2026-12-01T18:30', location: '본관 5층', capacity: 20,
  }));
  expect(mutations.addSession).not.toHaveBeenCalled();
  await waitFor(() => expect(onChanged).toHaveBeenCalled());
});

test('회차 작업이 401이면 onUnauthorized를 호출한다', () => {
  mutations.unauthorized = true;

  renderDialog();

  expect(onUnauthorized).toHaveBeenCalled();
});
