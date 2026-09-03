import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '');
  const backendTarget = env.VITE_MEDIA_SCREEN_BACKEND_TARGET || 'http://127.0.0.1:48080';

  return {
    plugins: [react()],
    server: {
      host: '0.0.0.0',
      port: 3009,
      proxy: {
        '/public-api': {
          target: backendTarget,
          changeOrigin: true,
        },
      },
    },
  };
});
