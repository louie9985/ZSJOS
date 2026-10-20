import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'node:url'
const path = (p: string) => fileURLToPath(new URL(p, import.meta.url)).replaceAll('\\','/')
export default defineConfig({root:path('./'),publicDir:path('../../public'),cacheDir:path('../../node_modules/.cache/student-info-copy-image'),plugins:[vue()],resolve:{alias:{'@':path('../../src')}},css:{preprocessorOptions:{scss:{additionalData:(source:string,filename:string)=>filename.includes('Qrcode.vue')?"@use '@/styles/variables.scss' as *;\n"+source:source}}},server:{host:'127.0.0.1',port:5192,strictPort:true,fs:{allow:[path('../../')]}}})
