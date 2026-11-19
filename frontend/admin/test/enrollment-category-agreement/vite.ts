import {defineConfig} from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import {resolve} from 'node:path'
const fixture=resolve(process.cwd(),'test/enrollment-category-agreement')
export default defineConfig({cacheDir:resolve('node_modules/.cache/agreement-fixture'),plugins:[vue(),AutoImport({imports:['vue',{'/test/enrollment-category-agreement/mock.ts':['useMessage']}],dts:false})],resolve:{alias:[
 ...['@/config/axios','@/utils/permission','@/store/modules/locale','@/components/UploadFile/src/useUpload'].map(find=>({find,replacement:fixture+'/mock.ts'})),{find:'@',replacement:resolve(process.cwd(),'src')}]},
 build:{outDir:resolve('node_modules/.cache/agreement-fixture-dist'),rollupOptions:{input:fixture+'/index.html'}}})
