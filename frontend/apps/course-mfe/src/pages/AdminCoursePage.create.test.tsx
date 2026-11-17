import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { adminCourseApi } from '../api/courseApi';
import { AdminCoursePage } from './AdminCoursePage';

// 훅은 실제로 쓰고 API 클라이언트만 mock해, 저장 → 닫기 → 목록 재조회 경로를 끝까지 검증한다
jest.mock('../api/courseApi', () => ({
  adminCourseApi: { getCourses: jest.fn(), getCourse: jest.fn(), createCourse: jest.fn() },
}));
const api = adminCourseApi as unknown as Record<'getCourses' | 'createCourse', jest.Mock>;

const empty = { data: { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 } };

function fillAndSave() {
  fireEvent.click(screen.getByRole('button', { name: '과정 등록' }));
  const dialog = screen.getByRole('dialog');
  fireEvent.change(within(dialog).getByLabelText(/제목/), { target: { value: '새 과정' } });
  fireEvent.change(within(dialog).getByLabelText('설명'), { target: { value: '설명' } });
  fireEvent.mouseDown(within(dialog).getByRole('combobox', { name: /분류/ }));
  fireEvent.click(screen.getByRole('option', { name: '데이터' }));
  fireEvent.click(within(dialog).getByRole('button', { name: '저장' }));
}

beforeEach(() => {
  api.getCourses.mockReset().mockResolvedValue(empty);
  api.createCourse.mockReset();
});

test('저장에 성공하면 대화상자가 닫히고 목록을 다시 조회한다', async () => {
  api.createCourse.mockResolvedValue({ data: { id: 9 } });
  render(<AdminCoursePage onUnauthorized={jest.fn()} />);
  await waitFor(() => expect(api.getCourses).toHaveBeenCalledTimes(1));

  fillAndSave();

  await waitFor(() => expect(api.createCourse).toHaveBeenCalledWith({ title: '새 과정', description: '설명', category: '데이터' }));
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  await waitFor(() => expect(api.getCourses).toHaveBeenCalledTimes(2));
});

test('저장에 실패하면 대화상자에 오류가 보이고 닫히지 않는다', async () => {
  api.createCourse.mockRejectedValue({ response: { status: 400, data: { message: '입력값이 올바르지 않습니다.' } } });
  render(<AdminCoursePage onUnauthorized={jest.fn()} />);

  fillAndSave();

  expect(await screen.findByText('입력값이 올바르지 않습니다.')).toBeInTheDocument();
  expect(screen.getByRole('dialog')).toBeInTheDocument();
});
