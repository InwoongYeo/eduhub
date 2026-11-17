import { Alert, Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';

interface ConfirmDialogProps {
  title: string;
  message: string;
  confirmLabel: string;
  pending: boolean;
  error?: string;
  onConfirm: () => void;
  onCancel: () => void;
}

/** 종료처럼 되돌릴 수 없는 작업의 확인 대화상자. 렌더링하면 열린다. */
export function ConfirmDialog({ title, message, confirmLabel, pending, error, onConfirm, onCancel }: ConfirmDialogProps) {
  return (
    <Dialog open onClose={onCancel}>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <DialogContentText>{message}</DialogContentText>
        {error && <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>}
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel} disabled={pending}>취소</Button>
        <Button color="error" onClick={onConfirm} disabled={pending}>{confirmLabel}</Button>
      </DialogActions>
    </Dialog>
  );
}
