/// <reference types="vitest/config" />
import { readFileSync } from 'node:fs';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { pwaPlugin } from './build/pwa';

const pkg = JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf8')) as { version: string };

export default defineConfig({
  plugins: [react(), pwaPlugin()],
  // Relative by default (works at / in dev and from any folder); GitHub Pages sets VITE_BASE=/<repo>/.
  base: process.env.VITE_BASE || './',
  define: {
    __APP_VERSION__: JSON.stringify(pkg.version),
  },
  server: {
    port: 5173,
    strictPort: true,
  },
  preview: {
    port: 4173,
  },
  build: {
    target: 'es2022',
    sourcemap: true,
  },
  test: {
    include: ['test/**/*.test.ts'],
    environment: 'node',
  },
});
