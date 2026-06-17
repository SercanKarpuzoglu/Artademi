import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// Port 5173 — Keycloak client'inda redirect URI olarak kayitli.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
  },
});
