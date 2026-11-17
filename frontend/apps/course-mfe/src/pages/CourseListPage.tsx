import { useState } from 'react';
import {
  Alert, Chip, CircularProgress, MenuItem, Stack, Table, TableBody, TableCell, TableHead, TablePagination, TableRow,
  TextField, Typography,
} from '@mui/material';
import { CATEGORIES } from '../constants/categories';
import { useCourses } from '../hooks/useCourses';

const PAGE_SIZE = 10;

/** 과정 상태 표시 (CourseStatus: OPEN, CLOSED) */
function StatusChip({ status }: { status: string }) {
  return status === 'OPEN'
    ? <Chip label="운영중" color="success" size="small" />
    : <Chip label="종료" size="small" />;
}

/** 과정 목록 화면 (FR-01). 데이터는 useCourses 훅에서만 받고, API를 직접 부르지 않는다. */
export function CourseListPage() {
  const [keyword, setKeyword] = useState('');
  const [category, setCategory] = useState('');
  const [page, setPage] = useState(0); // MUI TablePagination도 0부터 시작
  const { data, loading, error } = useCourses({ keyword, category, page, size: PAGE_SIZE });

  if (error) return <Alert severity="error">과정 목록을 불러오지 못했습니다.</Alert>;

  return (
    <Stack spacing={2}>
      <Typography variant="h5" component="h1">교육과정</Typography>
      <Stack direction="row" spacing={2}>
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
          label="분류"
          value={category}
          onChange={(e) => {
            setCategory(e.target.value);
            setPage(0);
          }}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="">전체</MenuItem>
          {CATEGORIES.map((c) => (
            <MenuItem key={c} value={c}>{c}</MenuItem>
          ))}
        </TextField>
      </Stack>
      {loading ? (
        <CircularProgress />
      ) : (
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>과정명</TableCell>
              <TableCell>분류</TableCell>
              <TableCell>상태</TableCell>
              <TableCell>회차 수</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {data?.content.map((c) => (
              <TableRow key={c.id}>
                <TableCell>{c.title}</TableCell>
                <TableCell>{c.category}</TableCell>
                <TableCell><StatusChip status={c.status} /></TableCell>
                <TableCell>{c.sessionCount}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
      <TablePagination
        component="div"
        count={data?.totalElements ?? 0}
        page={page}
        rowsPerPage={PAGE_SIZE}
        rowsPerPageOptions={[PAGE_SIZE]}
        onPageChange={(_, p) => setPage(p)}
      />
    </Stack>
  );
}
