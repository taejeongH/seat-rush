import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', '')

  return {
    plugins: [
      react(),
      {
        name: 'strip-local-proxy-browser-headers',
        configureServer(server) {
          server.middlewares.use('/api', (req, _res, next) => {
            delete req.headers.origin
            delete req.headers.referer
            delete req.headers['sec-fetch-site']
            delete req.headers['sec-fetch-mode']
            delete req.headers['sec-fetch-dest']
            next()
          })
        },
      },
    ],
    server: {
      port: 5173,
      proxy: env.VITE_API_PROXY_TARGET
        ? {
            '/api': {
              target: env.VITE_API_PROXY_TARGET,
              changeOrigin: true,
              secure: true,
              configure: (proxy) => {
                proxy.on('proxyReq', (proxyReq) => {
                  proxyReq.removeHeader('origin')
                })
              },
            },
          }
        : undefined,
    },
  }
})
