import { defineConfig, type Plugin, type Connect } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import http from 'node:http'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const mockApiHandler = require('../api/[...all].js')

function mockApiPlugin(): Plugin {
  let backendAvailable: boolean | null = null
  let lastCheck = 0

  async function isBackendUp(): Promise<boolean> {
    const now = Date.now()
    if (backendAvailable !== null && now - lastCheck < 4000) {
      return backendAvailable
    }
    return new Promise((resolve) => {
      const probe = http.get('http://127.0.0.1:8081/api/health', { timeout: 300 }, (res) => {
        backendAvailable = res.statusCode === 200
        lastCheck = Date.now()
        resolve(backendAvailable)
      })
      probe.on('error', () => {
        backendAvailable = false
        lastCheck = Date.now()
        resolve(false)
      })
      probe.on('timeout', () => {
        probe.destroy()
        backendAvailable = false
        lastCheck = Date.now()
        resolve(false)
      })
    })
  }

  const middleware: Connect.NextHandleFunction = async (req, res, next) => {
    if (req.url && (req.url === '/api' || req.url.startsWith('/api/') || req.url.startsWith('/api?'))) {
      const up = await isBackendUp()
      if (!up) {
        return mockApiHandler(req, res)
      }
    }
    next()
  }

  return {
    name: 'eduvision-mock-api',
    configureServer(server) {
      server.middlewares.use(middleware)
    },
    configurePreviewServer(server) {
      server.middlewares.use(middleware)
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss(), mockApiPlugin()],
  server: {
    port: 5173,
    // The Spring Boot backend lives on 8081 (8080 is the team's own internet proxy - never kill it).
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})