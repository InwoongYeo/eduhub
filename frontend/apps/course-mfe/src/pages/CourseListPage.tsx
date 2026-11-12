import { useState } from 'react';
import {
  Alert, CircularProgress, Stack, Table, TableBody, TableCell, TableHead, TablePagination, TableRow, TextField,
} from '@mui/material';
import { useCourses } from '../hooks/useCourses';

const PAGE_SIZE = 10;

/** 과정 목록 화면 (FR-01). 데이터는 useCourses 훅에서만 받고, API를 직접 부르지 않는다. */
export function CourseListPage() {
  const [keyword, setKeyword] = useState('');
  const [category, setCategory] = useState('');
  const [page, setPage] = useState(0); // MUI TablePagination도 0부터 시작
  const { data, loading, error } = useCourses({ keyword, category, page, size: PAGE_SIZE });

  if (error) return <Alert severity="error">과정 목록을 불러오지 못했습니다.</Alert>;

  return (
    <Stack spacing={2}>
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
          label="분류"
          value={category}
          onChange={(e) => {
            setCategory(e.target.value);
            setPage(0);
          }}
        />
      </Stack>
      {loading ? (
        <CircularProgress />
      ) : (
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>과정명</TableCell>
              <TableCell>분류</TableCell>
              <TableCell>회차 수</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {data?.content.map((c) => (
              <TableRow key={c.id}>
                <TableCell>{c.title}</TableCell>
                <TableCell>{c.category}</TableCell>
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
