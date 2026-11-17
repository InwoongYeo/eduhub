import { useEffect, useState } from 'react';
import type { CourseRequest, CourseSummaryResponse, GetCoursesStatusEnum } from '@eduhub/api-client';
import {
  Alert, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, Table, TableBody, TableCell, TableHead, TablePagination,
  TableRow, TextField, Typography,
} from '@mui/material';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { CourseDetailDialog } from '../components/CourseDetailDialog';
import { CourseFormDialog } from '../components/CourseFormDialog';
import { useAdminCourses } from '../hooks/useAdminCourses';
import { useCourseDetail } from '../hooks/useCourseDetail';
import { useCourseMutations } from '../hooks/useCourseMutations';

const PAGE_SIZE = 10;

type StatusFilter = '' | GetCoursesStatusEnum; // 빈 값은 전체

/** 선택한 과정에 대해 무엇을 보여 줄지: 상세 대화상자, 수정 폼 */
type Selection = { id: number; view: 'detail' | 'edit' };

function StatusChip({ status }: { status: string }) {
  return status === 'OPEN'
    ? <Chip label="운영중" color="success" size="small" />
    : <Chip label="종료" size="small" />;
}

/** 과정 관리 화면 (FR-03). 데이터는 훅에서만 받고 API를 직접 부르지 않는다. 401이면 onUnauthorized를 호출한다. */
export function AdminCoursePage({ onUnauthorized }: { onUnauthorized: () => void }) {
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<StatusFilter>('');
  const [page, setPage] = useState(0); // API와 MUI 모두 0부터, 표시만 +1
  const [reloadKey, setReloadKey] = useState(0);
  const [creating, setCreating] = useState(false);
  const [selection, setSelection] = useState<Selection>();
  const [closing, setClosing] = useState<CourseSummaryResponse>();

  const list = useAdminCourses({ keyword, status: status || undefined, page, size: PAGE_SIZE, reloadKey });
  const detail = useCourseDetail(selection?.id);
  const mutations = useCourseMutations();

  const unauthorized = list.unauthorized || detail.unauthorized || mutations.unauthorized;
  useEffect(() => {
    if (unauthorized) onUnauthorized();
  }, [unauthorized, onUnauthorized]);

  const reloadList = () => setReloadKey((k) => k + 1);
  // 이전 과정의 데이터가 남아 있을 수 있으므로 선택한 과정의 것만 쓴다
  const selected = detail.data && detail.data.id === selection?.id ? detail.data : undefined;

  function openCreate() {
    mutations.clearError();
    setCreating(true);
  }

  function openEdit(id: number) {
    mutations.clearError();
    setSelection({ id, view: 'edit' });
  }

  function openClose(course: CourseSummaryResponse) {
    mutations.clearError();
    setClosing(course);
  }

  async function create(values: CourseRequest) {
    if (await mutations.createCourse(values)) {
      setCreating(false);
      setPage(0);
      reloadList();
    }
  }

  async function update(values: CourseRequest) {
    if (!selection) return;
    if (await mutations.updateCourse(selection.id, values)) {
      setSelection(undefined);
      reloadList();
    }
  }

  async function confirmClose() {
    if (!closing) return;
    if (await mutations.closeCourse(closing.id)) {
      setClosing(undefined);
      reloadList();
    }
  }

  return (
    <Stack spacing={2}>
      <Typography variant="h5" component="h1">과정 관리</Typography>
      <Stack direction="row" spacing={2} alignItems="center">
        <TextField
          label="과정명 검색"
          value={keyword}
          onChange={(e) => {
            setKeyword(e.target.value);
            setPage(0); // 검색 조건이 바뀌면 첫 페이지부터
          }}
        />
        <TextField
          select
          label="상태"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as StatusFilter);
            setPage(0);
          }}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="">전체</MenuItem>
          <MenuItem value="OPEN">운영 중</MenuItem>
          <MenuItem value="CLOSED">종료</MenuItem>
        </TextField>
        <Button variant="contained" onClick={openCreate}>과정 등록</Button>
      </Stack>
      {list.error && <Alert severity="error">{list.error}</Alert>}
      {list.loading ? (
        <CircularProgress />
      ) : (
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>과정명</TableCell>
              <TableCell>분류</TableCell>
              <TableCell>상태</TableCell>
              <TableCell>회차 수</TableCell>
              <TableCell />
            </TableRow>
          </TableHead>
          <TableBody>
            {list.data?.content.map((c) => {
              const closed = c.status === 'CLOSED';
              return (
                <TableRow key={c.id} hover sx={{ cursor: 'pointer' }} onClick={() => setSelection({ id: c.id, view: 'detail' })}>
                  <TableCell>{c.title}</TableCell>
                  <TableCell>{c.category}</TableCell>
                  <TableCell><StatusChip status={c.status} /></TableCell>
                  <TableCell>{c.sessionCount}</TableCell>
                  <TableCell>
                    <Button
                      size="small"
                      disabled={closed}
                      onClick={(e) => {
                        e.stopPropagation(); // 행 클릭(상세)으로 번지지 않게
                        openEdit(c.id);
                      }}
                    >
                      수정
                    </Button>
                    <Button
                      size="small"
                      color="error"
                      disabled={closed}
                      onClick={(e) => {
                        e.stopPropagation();
                        openClose(c);
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
      )}
      <TablePagination
        component="div"
        count={list.data?.totalElements ?? 0}
        page={page}
        rowsPerPage={PAGE_SIZE}
        rowsPerPageOptions={[PAGE_SIZE]}
        onPageChange={(_, p) => setPage(p)}
      />

      {creating && (
        <CourseFormDialog
          title="과정 등록"
          pending={mutations.pending}
          error={mutations.error}
          onSubmit={create}
          onCancel={() => setCreating(false)}
        />
      )}
      {selection?.view === 'edit' && selected && (
        <CourseFormDialog
          title="과정 수정"
          initial={{ title: selected.title ?? '', description: selected.description, category: selected.category ?? '' }}
          pending={mutations.pending}
          error={mutations.error}
          onSubmit={update}
          onCancel={() => setSelection(undefined)}
        />
      )}
      {selection?.view === 'edit' && !selected && (
        <Dialog open onClose={() => setSelection(undefined)} fullWidth maxWidth="sm">
          <DialogTitle>과정 수정</DialogTitle>
          <DialogContent>
            {detail.error
              ? <Alert severity="error">{detail.error}</Alert>
              : <CircularProgress aria-label="불러오는 중" />}
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setSelection(undefined)}>닫기</Button>
          </DialogActions>
        </Dialog>
      )}
      {selection?.view === 'detail' && (
        <CourseDetailDialog
          detail={selected}
          loading={detail.loading}
          error={detail.error}
          onChanged={() => {
            detail.reload();
            reloadList(); // 회차 수가 바뀔 수 있다
          }}
          onUnauthorized={onUnauthorized}
          onClose={() => setSelection(undefined)}
        />
      )}
      {closing && (
        <ConfirmDialog
          title="과정 종료"
          message={`'${closing.title}' 과정을 종료합니다. 종료한 과정은 되돌릴 수 없습니다.`}
          confirmLabel="종료"
          pending={mutations.pending}
          error={mutations.error}
          onConfirm={confirmClose}
          onCancel={() => setClosing(undefined)}
        />
      )}
    </Stack>
  );
}
