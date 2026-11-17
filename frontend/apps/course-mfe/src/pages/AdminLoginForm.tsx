import { useState } from 'react';
import type { FormEvent } from 'react';
import { Alert, Button, Stack, TextField, Typography } from '@mui/material';
import { useAdminLogin } from '../hooks/useAdminLogin';

/** 관리자 로그인 폼. 성공하면 onLoggedIn을 호출한다 (토큰은 useAdminLogin이 저장한다). */
export function AdminLoginForm({ onLoggedIn }: { onLoggedIn: () => void }) {
  const { login, pending, error } = useAdminLogin();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (await login(email, password)) onLoggedIn();
  }

  return (
    <Stack component="form" spacing={2} onSubmit={handleSubmit} sx={{ maxWidth: 360 }}>
      <Typography variant="h5" component="h1">관리자 로그인</Typography>
      <TextField label="이메일" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
      <TextField
        label="비밀번호"
        type="password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        required
        autoComplete="current-password"
      />
      {error && <Alert severity="error">{error}</Alert>}
      <Button type="submit" variant="contained" disabled={pending}>로그인</Button>
    </Stack>
  );
}
