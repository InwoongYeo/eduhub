import { useState } from 'react';
import type { SessionRequest } from '@eduhub/api-client';
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField } from '@mui/material';

interface SessionFormDialogProps {
  title: string; // "회차 추가" | "회차 수정"
  initial?: SessionRequest;
  pending: boolean;
  error?: string;
  onSubmit: (values: SessionRequest) => void;
  onCancel: () => void;
}

/** datetime-local 입력은 "yyyy-MM-ddTHH:mm"까지만 받는다. 서버 값의 초 이하는 잘라낸다. */
const toLocalInput = (value?: string) => (value ? value.slice(0, 16) : '');

/** 회차 등록·수정 폼. 렌더링하면 열리고, 열릴 때마다 initial로 채워진다. */
export function SessionFormDialog({ title, initial, pending, error, onSubmit, onCancel }: SessionFormDialogProps) {
  const [startsAt, setStartsAt] = useState(toLocalInput(initial?.startsAt));
  const [endsAt, setEndsAt] = useState(toLocalInput(initial?.endsAt));
  const [location, setLocation] = useState(initial?.location ?? '');
  const [capacity, setCapacity] = useState(initial ? String(initial.capacity) : '');
  const capacityNumber = Number(capacity);
  const valid = startsAt !== '' && endsAt !== '' && location.trim() !== ''
    && Number.isInteger(capacityNumber) && capacityNumber >= 1;

  return (
    <Dialog open onClose={onCancel} fullWidth maxWidth="sm">
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField
            label="시작 일시"
            type="datetime-local"
            value={startsAt}
            onChange={(e) => setStartsAt(e.target.value)}
            required
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            label="종료 일시"
            type="datetime-local"
            value={endsAt}
            onChange={(e) => setEndsAt(e.target.value)}
            required
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            label="장소"
            value={location}
            onChange={(e) => setLocation(e.target.value)}
            required
            slotProps={{ htmlInput: { maxLength: 100 } }}
          />
          <TextField
            label="정원"
            type="number"
            value={capacity}
            onChange={(e) => setCapacity(e.target.value)}
            required
            slotProps={{ htmlInput: { min: 1 } }}
          />
          {error && <Alert severity="error">{error}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel} disabled={pending}>취소</Button>
        <Button
          variant="contained"
          disabled={!valid || pending}
          onClick={() => onSubmit({ startsAt, endsAt, location: location.trim(), capacity: capacityNumber })}
        >
          저장
        </Button>
      </DialogActions>
    </Dialog>
  );
}
