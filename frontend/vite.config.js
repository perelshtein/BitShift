import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path' // Node.js path module

export default defineConfig(({ mode }) => {
  // Load env file based on `mode` in the current working directory
  const env = loadEnv(mode, process.cwd());
  const SERVER_ROOT = env.VITE_SERVER_ROOT;

  return {
    server: {
      proxy: {
        '/course.xml': SERVER_ROOT
      },
    },
    plugins: [react()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src'),
      },
    },
    build: {
      // для отладки включить
      // sourcemap: true,
      // minify: false,

      rollupOptions: {
        output: {
          manualChunks(id) {
            if (id.includes('admin')) return 'admin';
            if (id.includes('public')) return 'website';
            return 'vendor';
          },
        },
      },
    },
  }
})
