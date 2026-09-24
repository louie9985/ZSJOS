// UTF-8. Bundle the actual theme/provider/component into an offline review artifact.
import { build } from 'vite'
import react from '@vitejs/plugin-react'
import { writeFile } from 'node:fs/promises'
const result = await build({ configFile: false, plugins: [react()], define: { 'process.env.NODE_ENV': JSON.stringify('production') }, build: {
  write: false, minify: true, lib: { entry: 'test/home-announcement-demo.tsx', formats: ['iife'], name: 'AnnouncementDemo' },
  rollupOptions: { output: { inlineDynamicImports: true } },
} })
const output = (Array.isArray(result) ? result[0] : result).output
const js = output.filter(x => x.type === 'chunk').map(x => x.code).join('\n').replace(/<\/script/gi, '<\\/script')
const css = output.filter(x => x.type === 'asset' && x.fileName.endsWith('.css')).map(x => String(x.source)).join('\n').replace(/@font-face\s*\{[^}]*\}/g, '')
await writeFile('../../output/announcement-demo.html', `<!doctype html><html lang="zh-CN"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>中视健 · 公告主题与动效预览</title><style>${css}</style></head><body><div id="root"></div><script>${js}</script></body></html>`, 'utf8')
