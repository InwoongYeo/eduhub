import { useState } from 'react';
import type { CourseRequest } from '@eduhub/api-client';
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField } from '@mui/material';
import { CATEGORIES } from '../constants/categories';

interface CourseFormDialogProps {
  title: string; // "과정 등록" | "과정 수정"
  initial?: CourseRequest;
  pending: boolean;
  error?: string;
  onSubmit: (values: CourseRequest) => void;
  onCancel: () => void;
}

/** 과정 등록·수정 폼. 렌더링하면 열리고, 열릴 때마다 initial로 채워진다. */
export function CourseFormDialog({ title, initial, pending, error, onSubmit, onCancel }: CourseFormDialogProps) {
  const [name, setName] = useState(initial?.title ?? '');
  const [description, setDescription] = useState(initial?.description ?? '');
  const [category, setCategory] = useState(initial?.category ?? '');
  const valid = name.trim() !== '' && category !== '';

  return (
    <Dialog open onClose={onCancel} fullWidth maxWidth="sm">
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField
            label="제목"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            slotProps={{ htmlInput: { maxLength: 100 } }}
          />
          <TextField
            label="설명"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            multiline
            minRows={3}
            slotProps={{ htmlInput: { maxLength: 1000 } }}
          />
          <TextField select label="분류" value={category} onChange={(e) => setCategory(e.target.value)} required>
            {CATEGORIES.map((c) => (
              <MenuItem key={c} value={c}>{c}</MenuItem>
            ))}
          </TextField>
          {error && <Alert severity="error">{error}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel} disabled={pending}>취소</Button>
        <Button
          variant="contained"
          disabled={!valid || pending}
          // 설명이 비면 필드를 생략한다. 백엔드 Course.update는 description을 그대로 덮어쓰므로 null로 비워진다
          onClick={() => onSubmit({ title: name.trim(), description: description.trim() || undefined, category })}
        >
          저장
        </Button>
      </DialogActions>
    </Dialog>
  );
}
