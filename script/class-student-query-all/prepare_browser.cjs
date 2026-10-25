// UTF-8. Compile actual React/Vue pages against isolated transport fixtures.
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../..');
const workbench = path.join(root, 'frontend/workbench');
const admin = path.join(root, 'frontend/admin');
const out = path.join(workbench, 'tmp/class-student-query-all/browser');
fs.mkdirSync(out, { recursive: true });
const esbuild = require(path.join(workbench, 'node_modules/esbuild'));
const { parse, compileScript } = require(path.join(admin, 'node_modules/vue/compiler-sfc'));
const write = (name, body) => fs.writeFileSync(path.join(out, name), body, 'utf8');
const absolute = value => value.replaceAll('\\', '/');
write('fixture.ts', `
const params = new URLSearchParams(location.search);
export const view = params.get('view') || 'students';
const mode = params.get('mode') || 'read';
const feature = ['feature','allowed'].includes(mode), object = ['object','allowed'].includes(mode);
export const permissions = [
  ...(mode === 'none' ? [] : [view === 'classes' ? params.has('class-my') ? 'zsjos:delivery-class:query-my' : 'zsjos:delivery-class:query' : 'zsjos:student:query-my']),
  view === 'classes' ? 'zsjos:delivery-class:query-all' : 'zsjos:student:query-all',
  ...(params.has('create-only') ? ['zsjos:delivery-class:create'] : []),
  ...(params.has('class-my') ? ['zsjos:delivery-class:query-my', 'zsjos:delivery-class:query-all'] : []),
  ...(feature ? ['zsjos:delivery-class:query-managed','zsjos:delivery-class:create','zsjos:delivery-class:update','zsjos:delivery-class:complete','zsjos:delivery-class:group-delete','zsjos:delivery-class:direct-transfer','zsjos:class-transfer:create','zsjos:student-contact:submit','zsjos:student:update-basic-info','zsjos:student-service:close'] : []),
];
export const checkPermi = values => values.some(value => permissions.includes(value));
export const useUserStore = () => ({ dataAccess:{tenantReadAll:false}, getPermissions:new Set(permissions) });
export const state = { calls:[], unexpected:[], writes:[], allowWrite:undefined, fail:params.has('error'), empty:params.has('empty') };
Object.assign(window,{fixture:state});
const actions = object ? ['UPDATE','MOVE_GROUP','COMPLETE'] : [];
const row = { id:11,className:'全范围班级',classNo:'BJ-TEST',classKind:'NORMAL',systemClass:false,status:'SERVING',studentCount:1,version:1,homeroomUserId:7,homeroomUserNameSnapshot:'测试规划师',availableActions:actions };
const student = { personId:7,personNo:'XY-TEST',name:'全范围学员',serviceRelationId:1,service:{serviceRelationId:1,leadId:5,courseName:'全范围课程',classId:11,className:'全范围班级',ownerUserId:7,status:'active',acceptanceStatus:'accepted',owner:false,version:1} };
const context = { serviceRelationId:1,version:1,availableActions:object?['FOLLOW_UP','EDIT_BASIC_INFO','DIRECT_TRANSFER','REQUEST_TRANSFER']:[],visibleTabs:['student-info'],quickNotes:[],firstContactChecklist:[],acceptanceStatus:'accepted' };
export async function request(method,url,query={},data) {
  state.calls.push({method,url,query});
  if(method !== 'get') {
    const payload=typeof data==='string'?JSON.parse(data):data;
    state.writes.push({method,url,data:payload});
    if(method==='post' && state.allowWrite===url) return url.endsWith('/group/create-classes')?{classIds:[21,22]}:true;
    throw Error('Unexpected write');
  }
  if(url.includes('advanced-filter/catalog')) return {fields:[],relativeDateOptions:[]};
  if(url.includes('advanced-filter-template/visible-list') || url.includes('/dict-data/')) return [];
  if(url.endsWith('filter-context')) return {projects:[],homerooms:[]};
  if(url.endsWith('/homeroom-candidates')) return [{id:7,name:'测试规划师甲',deptId:1},{id:8,name:'测试规划师乙',deptId:1}];
  if(url.endsWith('/group/options') || url.endsWith('/group/candidates')) return {list:[],total:0};
  if(url.endsWith('/options')) return [];
  if(url.includes('/group/') && url.endsWith('page')) {
    if(state.fail) throw Error('班级列表测试失败');
    const nodes=state.empty?[]:[{key:'class:11',type:'CLASS',name:row.className,single:row,classCount:1,homeroomCount:1,serviceCount:1,servingCount:1,completedCount:0,examDate:'未设置',projects:[],autoExpand:false}];
    return {list:nodes,total:nodes.length,classTotal:nodes.length};
  }
  if(url.startsWith('/zsjos/delivery-class/') && url.endsWith('page')) {
    if(state.fail) throw Error('班级列表测试失败');
    return {list:state.empty?[]:[row],total:state.empty?0:1};
  }
  if(url.endsWith('/11/students')) return {list:[{serviceRelationId:1,personId:7,studentName:student.name,serviceStatus:'active',version:1,availableActions:object?['DIRECT_TRANSFER','REQUEST_TRANSFER']:[]}],total:1};
  if(url.endsWith('/my-service-page')) {
    if(state.fail) throw Error('学员列表测试失败');
    return {list:state.empty?[]:[student],total:state.empty?0:1};
  }
  if(url.endsWith('/my-service/1')) return structuredClone(student);
  if(url.endsWith('/contact-context')) return structuredClone(context);
  if(url.endsWith('/contact-records')) return {list:[],total:0};
  if(url.endsWith('/lifecycle')) return {closed:false,history:[],availableActions:object?['CLOSE_SERVICE']:[]};
  state.unexpected.push(url); throw Error('Unexpected request: '+url);
}
export default Object.fromEntries(['get','post','put','delete'].map(method=>[method,({url,params,data})=>request(method,url,params,data)]));
`);
write('react.tsx', `
import {createRoot} from 'react-dom/client';
import {App} from 'antd';
import {MemoryRouter} from 'react-router-dom';
import ThemeProvider from '${absolute(path.join(workbench,'src/components/Theme/ThemeProvider'))}';
import DeliveryClassPage from '${absolute(path.join(workbench,'src/pages/DeliveryClassPage'))}';
import {MyStudentsPage} from '${absolute(path.join(workbench,'src/pages/RegistrationPages'))}';
import {http} from '${absolute(path.join(workbench,'src/services/api'))}';
import {view,permissions,request} from './fixture';
import '${absolute(path.join(workbench,'src/styles/index.css'))}';
localStorage.setItem('crm-theme',JSON.stringify({inboxLayoutMode:innerWidth<600?'split':'table'}));
http.defaults.adapter=async config=>({config,status:200,statusText:'OK',headers:{},data:{code:0,data:await request(config.method,config.url,config.params,config.data)}});
createRoot(document.getElementById('root')).render(<ThemeProvider><App><MemoryRouter>{view==='classes'?<DeliveryClassPage permissions={permissions}/>:<MyStudentsPage permissions={permissions}/>}</MemoryRouter></App></ThemeProvider>);
`);
write('vue.ts', `
import {createApp,h} from 'vue';
import {createRouter,createMemoryHistory} from 'vue-router';
import ElementPlus,{ElDialog,ElMessage} from 'element-plus';import 'element-plus/dist/index.css';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import Classes from '${absolute(path.join(admin,'src/views/zsjos/class-management.vue'))}';
import Students from '${absolute(path.join(admin,'src/views/zsjos/my-students.vue'))}';
import {view,checkPermi} from './fixture';
globalThis.useMessage=()=>ElMessage;
async function start(){
  const Page=view==='classes'?Classes:Students;
  const router=createRouter({history:createMemoryHistory(),routes:[{path:'/fixture',component:Page}]});
  const app=createApp(Page);app.use(ElementPlus,{locale:zhCn}).use(router);
  app.component('ContentWrap',{render(){return h('section',this.$slots.default?.())}});
  app.component('Dialog',ElDialog);
  for(const name of ['Pagination','Icon'])app.component(name,{render(){return null}});
  app.directive('hasPermi',{mounted(el,binding){if(!checkPermi(binding.value))el.remove()}});
  await router.push('/fixture');await router.isReady();app.mount('#root');
}
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
      build.onResolve({filter:/^@\/(config\/axios|store\/modules\/user|utils\/permission)$/},()=>({path:path.join(out,'fixture.ts')}));
      build.onResolve({filter:/(ZsjosAdvancedFilter|StudentPartnerBinding|StudentArchivePanel|BusinessReadScope|StudentEntryForm|ClassExamCalendar).*\.vue$/},()=>({path:'empty-component',namespace:'stub'}));
      build.onLoad({filter:/.*/,namespace:'stub'},()=>({contents:'export default {render(){return null}}'}));
      build.onLoad({filter:/\.vue$/},args=>{
        let source=fs.readFileSync(args.path,'utf8');
        if(/(my-students|class-management)\.vue$/.test(args.path))source=source.replace(/<script[^>]*setup[^>]*>/, '$&\nimport {ref,reactive,watch,computed,onMounted} from "vue";import {useRoute} from "vue-router";');
        const {descriptor}=parse(source);const result=compileScript(descriptor,{id:'query-all',inlineTemplate:true});
        return {contents:result.content,loader:'ts',resolveDir:path.dirname(args.path)};
      });
    }}]});
  for(const client of ['react','vue'])write(client+'.html',
    '<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><link rel="stylesheet" href="'+client+'.css"><div id="root"></div><script src="'+client+'.js"></script></html>');
}
build().catch(error=>{console.error(error);process.exit(1)});
