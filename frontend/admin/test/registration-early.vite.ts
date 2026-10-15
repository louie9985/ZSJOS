import {defineConfig} from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import {resolve} from 'node:path'
export default defineConfig({cacheDir:'node_modules/.cache/registration-early',plugins:[{name:'isolate-filter',enforce:'pre',resolveId(s){if(s.endsWith('ZsjosAdvancedFilter.vue'))return '\0registration-filter'},load(id){if(id==='\0registration-filter')return 'export default {render(){return null}}'}},vue(),AutoImport({imports:['vue'],dts:false})],optimizeDeps:{entries:['test/registration-early.html']},resolve:{alias:[...['@/config/axios','@/store/modules/user','@/hooks/web/useMessage'].map(find=>({find,replacement:resolve(process.cwd(),'test/registration-early-request.ts')})),{find:'@',replacement:resolve(process.cwd(),'src')}]},server:{host:'127.0.0.1',port:5298,strictPort:true}})
