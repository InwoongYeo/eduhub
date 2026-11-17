import { fireEvent, render, screen } from '@testing-library/react';
import { CourseFormDialog } from './CourseFormDialog';

test('수정에서 설명을 비우고 제출하면 description 없이 보낸다 (백엔드는 null로 덮어써 비운다)', () => {
  const onSubmit = jest.fn();
  render(
    <CourseFormDialog
      title="과정 수정"
      initial={{ title: '자바 기초', description: '기존 설명', category: '개발' }}
      pending={false}
      onSubmit={onSubmit}
      onCancel={jest.fn()}
    />,
  );

  fireEvent.change(screen.getByLabelText('설명'), { target: { value: '   ' } });
  fireEvent.click(screen.getByRole('button', { name: '저장' }));

  expect(onSubmit).toHaveBeenCalledWith({ title: '자바 기초', description: undefined, category: '개발' });
});

test('필수값이 없으면 저장 버튼이 비활성화된다', () => {
  render(<CourseFormDialog title="과정 등록" pending={false} onSubmit={jest.fn()} onCancel={jest.fn()} />);

  expect(screen.getByRole('button', { name: '저장' })).toBeDisabled();
});
