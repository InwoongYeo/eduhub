import { useEffect, useState } from 'react';
import type { CourseDetailResponse, SessionResponse } from '@eduhub/api-client';
import {
  Alert, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Stack, Table, TableBody,
  TableCell, TableHead, TableRow, Typography,
} from '@mui/material';
import { useCourseMutations } from '../hooks/useCourseMutations';
import { ConfirmDialog } from './ConfirmDialog';
import { SessionFormDialog } from './SessionFormDialog';

interface CourseDetailDialogProps {
  detail?: CourseDetailResponse;
  loading: boolean;
  error?: string;
  onChanged: () => void; // 회차를 바꾼 뒤 상세·목록을 다시 불러온다
  onUnauthorized: () => void;
  onClose: () => void;
}

type SessionForm = { mode: 'add' } | { mode: 'edit'; session: SessionResponse };

const formatDateTime = (value?: string) => (value ? value.replace('T', ' ').slice(0, 16) : '');

/** 과정 상세 대화상자: 회차 표와 회차 추가·수정·종료. 렌더링하면 열린다. */
export function CourseDetailDialog({ detail, loading, error, onChanged, onUnauthorized, onClose }: CourseDetailDialogProps) {
  const mutations = useCourseMutations();
  const [form, setForm] = useState<SessionForm>();
  const [closing, setClosing] = useState<SessionResponse>();
  const courseClosed = detail?.status === 'CLOSED';

  useEffect(() => {
    if (mutations.unauthorized) onUnauthorized();
  }, [mutations.unauthorized, onUnauthorized]);

  const openForm = (next: SessionForm) => {
    mutations.clearError();
    setForm(next);
  };

  async function submit(values: Parameters<typeof mutations.addSession>[1]) {
    if (!detail?.id || !form) return;
    const ok = form.mode === 'add'
      ? await mutations.addSession(detail.id, values)
      : await mutations.updateSession(form.session.id!, values);
    if (ok) {
      setForm(undefined);
      onChanged();
    }
  }

  async function confirmClose() {
    if (!closing?.id) return;
    if (await mutations.closeSession(closing.id)) {
      setClosing(undefined);
      onChanged();
    }
  }

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>{detail?.title ?? '과정 상세'}</DialogTitle>
      <DialogContent>
        {error && <Alert severity="error">{error}</Alert>}
        {loading && !detail && <CircularProgress />}
        {detail && (
          <Stack spacing={2}>
            {detail.description && <Typography>{detail.description}</Typography>}
            <Stack direction="row" justifyContent="space-between" alignItems="center">
              <Typography variant="subtitle1" component="h3">회차</Typography>
              <Button variant="contained" size="small" disabled={courseClosed} onClick={() => openForm({ mode: 'add' })}>
                회차 추가
              </Button>
            </Stack>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>번호</TableCell>
                  <TableCell>일정</TableCell>
                  <TableCell>장소</TableCell>
                  <TableCell>정원</TableCell>
                  <TableCell>남은 자리</TableCell>
                  <TableCell>상태</TableCell>
                  <TableCell />
                </TableRow>
              </TableHead>
              <TableBody>
                {detail.sessions?.map((s) => {
                  const closed = courseClosed || s.status === 'CLOSED';
                  return (
                    <TableRow key={s.id}>
                      <TableCell>{s.sessionNo}</TableCell>
                      <TableCell>{formatDateTime(s.startsAt)} ~ {formatDateTime(s.endsAt)}</TableCell>
                      <TableCell>{s.location}</TableCell>
                      <TableCell>{s.capacity}</TableCell>
                      <TableCell>{s.remainingSeats}</TableCell>
                      <TableCell>
                        {s.status === 'OPEN'
                          ? <Chip label="운영중" color="success" size="small" />
                          : <Chip label="종료" size="small" />}
                      </TableCell>
                      <TableCell>
                        <Button size="small" disabled={closed} onClick={() => openForm({ mode: 'edit', session: s })}>
                          수정
                        </Button>
                        <Button
                          size="small"
                          color="error"
                          disabled={closed}
                          onClick={() => {
                            mutations.clearError();
                            setClosing(s);
                          }}
                        >
                          종료
                        </Button>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </Stack>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>닫기</Button>
      </DialogActions>
      {form && (
        <SessionFormDialog
          title={form.mode === 'add' ? '회차 추가' : '회차 수정'}
          initial={form.mode === 'edit'
            ? {
              startsAt: form.session.startsAt ?? '',
              endsAt: form.session.endsAt ?? '',
              location: form.session.location ?? '',
              capacity: form.session.capacity ?? 0,
            }
            : undefined}
          pending={mutations.pending}
          error={mutations.error}
          onSubmit={submit}
          onCancel={() => setForm(undefined)}
        />
      )}
      {closing && (
        <ConfirmDialog
          title="회차 종료"
          message={`${closing.sessionNo}회차를 종료합니다. 종료한 회차는 되돌릴 수 없습니다.`}
          confirmLabel="종료"
          pending={mutations.pending}
          error={mutations.error}
          onConfirm={confirmClose}
          onCancel={() => setClosing(undefined)}
        />
      )}
    </Dialog>
  );
}
