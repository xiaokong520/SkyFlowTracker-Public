import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  // DEV_* values configure only the Vite server. Never expose secrets via VITE_*.
  const env = loadEnv(mode, process.cwd(), '')
  const mediaBasePath = '/' + (env.VITE_MEDIA_BASE_PATH || '/SkyFlowTracker').replace(/^\/+|\/+$/g, '')

  return {
    plugins: [vue()],
    server: {
      host: env.DEV_HOST || '127.0.0.1',
      proxy: {
        '/api': {
          target: env.DEV_API_TARGET || 'http://localhost:8080',
          changeOrigin: true
        },
        '/ws': {
          target: env.DEV_WS_TARGET || 'ws://localhost:8080',
          ws: true
        },
        '/nginx': {
          target: env.DEV_MEDIA_TARGET || 'http://localhost',
          changeOrigin: true,
          rewrite: (url) => url.replace(/^\/nginx(?=\/|\?|$)/, mediaBasePath)
        }
      }
    }
  }
})
