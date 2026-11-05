import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Em desenvolvimento (npm run dev) o proxy imita o nginx de produção (API dos nós em 18081-18083).
const no = (porta) => ({ target: `http://localhost:${porta}`, rewrite: (p) => p.replace(/^\/api\/\w+\/loja/, '/api/loja') });
export default defineConfig({
  plugins: [react()],
  server: { proxy: { '/api/norte': no(18081), '/api/centro': no(18082), '/api/sul': no(18083) } },
});
