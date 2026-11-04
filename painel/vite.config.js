import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Em desenvolvimento (npm run dev) o proxy imita o nginx de produção.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api/norte': { target: 'http://localhost:8081', rewrite: (p) => p.replace(/^\/api\/norte/, '/api') },
      '/api/centro': { target: 'http://localhost:8082', rewrite: (p) => p.replace(/^\/api\/centro/, '/api') },
      '/api/sul': { target: 'http://localhost:8083', rewrite: (p) => p.replace(/^\/api\/sul/, '/api') },
      '/api/fornecedor': { target: 'http://localhost:8090', rewrite: (p) => p.replace(/^\/api\/fornecedor/, '/api') },
    },
  },
});
