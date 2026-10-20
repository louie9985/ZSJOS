import {defineConfig} from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import {resolve} from 'node:path'
export default defineConfig({cacheDir:'node_modules/.cache/student-order-name',plugins:[{name:'isolate-unrelated',enforce:'pre',resolveId(s){if(['ZsjosAdvancedFilter.vue','StudentPartnerBinding.vue','StudentArchivePanel.vue','BusinessReadScope/index.vue'].some(x=>s.endsWith(x)))return '\0name-stub'},load(id){if(id==='\0name-stub')return 'export default {render(){return null}}'}},vue(),AutoImport({imports:['vue',{'/test/student-order-name-request.ts':['useRoute']}],dts:false})],optimizeDeps:{entries:['test/student-order-name.html']},resolve:{alias:[...['@/config/axios','@/store/modules/user','@/utils/permission'].map(find=>({find,replacement:resolve(process.cwd(),'test/student-order-name-request.ts')})),{find:'@',replacement:resolve(process.cwd(),'src')}]},server:{host:'127.0.0.1',port:5317,strictPort:true}})
