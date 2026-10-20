// UTF-8. Bundle real pages with isolated API fixtures; never contact a business runtime.
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../..');
const workbench = path.join(root, 'frontend/workbench');
const admin = path.join(root, 'frontend/admin');
const out = path.join(workbench, 'tmp/student-read-scope/browser');
fs.mkdirSync(out, { recursive: true });
const esbuild = require(path.join(workbench, 'node_modules/esbuild'));
const { parse, compileScript } = require(path.join(admin, 'node_modules/vue/compiler-sfc'));
const write = (name, body) => fs.writeFileSync(path.join(out, name), body, 'utf8');
const absolute = p => p.replaceAll('\\', '/');
write('react.tsx', `
import {createRoot} from 'react-dom/client';
import {App} from 'antd';
import {MemoryRouter,useNavigate} from 'react-router-dom';
import ThemeProvider from '${absolute(path.join(workbench, 'src/components/Theme/ThemeProvider'))}';
import {MyStudentsPage} from '${absolute(path.join(workbench, 'src/pages/RegistrationPages'))}';
import {api,http} from '${absolute(path.join(workbench, 'src/services/api'))}';
import '${absolute(path.join(workbench, 'src/styles/index.css'))}';
const params=new URLSearchParams(location.search);
localStorage.setItem('crm-theme',JSON.stringify({inboxLayoutMode:params.get('layout')||'table'}));
const state={requests:[],details:[],writes:[],denyContact:false}; window.fixture=state;
const row={personId:7,personNo:'TEST-7',name:'范围测试学员',serviceRelationId:1,service:{serviceRelationId:1,courseName:'本人课程',classId:11,className:'本人班级',ownerUserId:7,status:'active',acceptanceStatus:'accepted',owner:true,version:1}};
const denied=()=>Object.assign(new Error('无权查看该课程'),{code:403});
http.defaults.adapter=async()=>{throw Error('Unexpected live request in isolated fixture')};
api.myStudentServices=async q=>{state.requests.push(q);const rows=q.classId===51?[]:[row];return {list:structuredClone(rows),total:rows.length}};
api.myStudentService=async id=>{state.details.push(id);if(id!==1)throw denied();return structuredClone(row)};
api.studentContactContext=async id=>{if(id!==1||state.denyContact)throw denied();return {serviceRelationId:1,version:1,availableActions:['FOLLOW_UP'],visibleTabs:[],quickNotes:[],firstContactChecklist:[],acceptanceStatus:'accepted'}};
api.studentContactRecords=async id=>{if(id!==1||state.denyContact)throw denied();return {list:[],total:0}};
api.advancedFilterCatalog=async()=>({fields:[],relativeDateOptions:[]});
api.advancedFilterTemplates=async()=>[];api.dictDataByType=async()=>[];
api.deliveryClasses.page=async()=>({list:[],total:0});
function Probe(){const navigate=useNavigate();window.navigateToService=id=>navigate('/zsjos/my-students',{state:{serviceRelationId:id}});return null}
const nav=params.has('denied')?{serviceRelationId:2}:params.has('legacy')?{personId:7}:params.has('class')?{classId:51}:undefined;
createRoot(document.getElementById('root')).render(<ThemeProvider><App><MemoryRouter initialEntries={[{pathname:'/zsjos/my-students',state:nav}]}><Probe/><MyStudentsPage permissions={['zsjos:student:query-my','zsjos:delivery-class:query','zsjos:student-contact:submit']}/></MemoryRouter></App></ThemeProvider>);
`);
write('admin-api.ts', `
export const useUserStore=()=>({dataAccess:{tenantReadAll:false},getPermissions:new Set()});
export const checkPermi=()=>false;
const row={personId:7,name:'范围测试学员',serviceRelationId:1,service:{serviceRelationId:1,courseName:'本人课程',className:'本人班级',status:'active'}};
window.fixture={requests:[],details:[]};
export default {get:async({url,params})=>{window.fixture.requests.push({url,params});if(url.endsWith('/my-service-page'))return {list:[structuredClone(row)],total:1};const id=Number(url.split('/').at(-1));window.fixture.details.push(id);if(id!==1)throw Object.assign(new Error('无权查看该课程'),{code:403});return structuredClone(row)}};
`);
write('vue.ts', `
import {createApp,h} from 'vue';
import {createRouter,createMemoryHistory} from 'vue-router';
import ElementPlus from 'element-plus';import 'element-plus/dist/index.css';
import Page from '${absolute(path.join(admin, 'src/views/zsjos/my-students.vue'))}';
async function start(){
const router=createRouter({history:createMemoryHistory(),routes:[{path:'/fixture',component:Page}]});
const app=createApp(Page);app.use(ElementPlus).use(router);
for(const name of ['ContentWrap'])app.component(name,{render(){return h('section',this.$slots.default?.())}});
for(const name of ['Pagination','Icon'])app.component(name,{render(){return null}});
window.navigateToService=id=>router.push({path:'/fixture',query:{serviceRelationId:id}});
const params=new URLSearchParams(location.search);
await router.push({path:'/fixture',query:params.has('denied')?{serviceRelationId:2}:params.has('legacy')?{personId:7}:{}});
await router.isReady();app.mount('#root');}
start();
`);
async function build() {
  await esbuild.build({entryPoints:[path.join(out,'react.tsx')],bundle:true,outfile:path.join(out,'react.js'),format:'iife',
    nodePaths:[path.join(workbench,'node_modules')],define:{'import.meta.env':'{}','process.env.NODE_ENV':'"development"'},
    loader:{'.svg':'dataurl','.png':'dataurl','.woff':'dataurl','.woff2':'dataurl'}});
  await esbuild.build({entryPoints:[path.join(out,'vue.ts')],bundle:true,outfile:path.join(out,'vue.js'),format:'iife',
    nodePaths:[path.join(admin,'node_modules')],alias:{'@':path.join(admin,'src')},
    define:{'process.env.NODE_ENV':'"development"',__VUE_OPTIONS_API__:'true',__VUE_PROD_DEVTOOLS__:'false',__VUE_PROD_HYDRATION_MISMATCH_DETAILS__:'false'},
    plugins:[{name:'isolated-vue',setup(build){
      build.onResolve({filter:/^@\/(config\/axios|store\/modules\/user|utils\/permission)$/},()=>({path:path.join(out,'admin-api.ts')}));
      build.onResolve({filter:/(ZsjosAdvancedFilter|StudentPartnerBinding|StudentArchivePanel|BusinessReadScope).*\.vue$/},()=>({path:'empty-component',namespace:'stub'}));
      build.onLoad({filter:/.*/,namespace:'stub'},()=>({contents:'export default {render(){return null}}'}));
      build.onLoad({filter:/\.vue$/},args=>{
        let source=fs.readFileSync(args.path,'utf8');
        if(args.path.endsWith('my-students.vue'))source=source.replace('<script lang="ts" setup>', '<script lang="ts" setup>\nimport {ref,reactive,watch} from "vue";import {useRoute} from "vue-router";');
        const {descriptor}=parse(source);const result=compileScript(descriptor,{id:'read-scope',inlineTemplate:true});
        return {contents:result.content,loader:'ts',resolveDir:path.dirname(args.path)};
      });
    }}]});
  for(const client of ['react','vue'])write(client+'.html',`<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="${client}.css"><div id="root"></div><script src="${client}.js"></script></html>`);
}
build().catch(error=>{console.error(error);process.exit(1)});
