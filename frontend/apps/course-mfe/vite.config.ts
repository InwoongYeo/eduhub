import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 단독 실행(포트 3001). /api 요청은 개발 서버 프록시로 백엔드(8080)에 넘긴다 (D-25: CORS 설정 대신 프록시)
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3001,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
});
