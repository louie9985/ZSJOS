import {defineConfig} from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import {resolve} from 'node:path'
export default defineConfig({cacheDir:'node_modules/.cache/service-subject',plugins:[{name:'isolate-filter',enforce:'pre',resolveId(s){if(['ZsjosAdvancedFilter.vue','StudentPartnerBinding.vue'].some(x=>s.endsWith(x)) || s.includes('BusinessReadScope'))return '\0registration-filter'},load(id){if(id==='\0registration-filter')return 'export default {render(){return null}}'}},vue(),AutoImport({imports:['vue','vue-router'],dts:false})],optimizeDeps:{entries:['test/service-subject.html']},resolve:{alias:[...['@/config/axios','@/store/modules/user','@/hooks/web/useMessage','@/utils/permission'].map(find=>({find,replacement:resolve(process.cwd(),'test/service-subject-request.ts')})),{find:'@',replacement:resolve(process.cwd(),'src')}]},server:{host:'127.0.0.1',port:5296,strictPort:true}})
