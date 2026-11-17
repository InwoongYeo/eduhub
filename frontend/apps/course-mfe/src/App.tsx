import { useCallback, useState } from 'react';
import { Box, Button, Container, Stack, Tab, Tabs } from '@mui/material';
import { clearToken, getToken } from './auth/adminSession';
import { AdminCoursePage } from './pages/AdminCoursePage';
import { AdminLoginForm } from './pages/AdminLoginForm';
import { CourseListPage } from './pages/CourseListPage';

/** 상단 탭으로 과정 조회 / 과정 관리를 전환한다 (D-38, 라우터 없이 상태로 전환). */
export function App() {
  const [tab, setTab] = useState<'list' | 'admin'>('list');
  const [loggedIn, setLoggedIn] = useState(() => getToken() !== null);

  // 토큰은 adminSession(sessionStorage)에만 두고, 여기서는 로그인 여부만 안다
  const handleUnauthorized = useCallback(() => setLoggedIn(false), []);
  const logout = () => {
    clearToken();
    setLoggedIn(false);
  };

  return (
    <Container sx={{ py: 4 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
        <Tabs value={tab} onChange={(_, value) => setTab(value)}>
          <Tab label="과정 조회" value="list" />
          <Tab label="과정 관리" value="admin" />
        </Tabs>
        {tab === 'admin' && loggedIn && <Button onClick={logout}>로그아웃</Button>}
      </Stack>
      <Box>
        {tab === 'list' && <CourseListPage />}
        {tab === 'admin' && (loggedIn
          ? <AdminCoursePage onUnauthorized={handleUnauthorized} />
          : <AdminLoginForm onLoggedIn={() => setLoggedIn(true)} />)}
      </Box>
    </Container>
  );
}
