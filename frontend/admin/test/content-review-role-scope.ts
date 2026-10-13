// UTF-8. Isolated designer with synthetic role metadata and no real API writes.
import { createApp, h, ref, provide, onMounted } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import UserTaskNodeConfig from '../src/components/SimpleProcessDesignerV2/src/nodes-config/UserTaskNodeConfig.vue'
import { setupI18n } from '../src/plugins/vueI18n'
import { setupStore } from '../src/store'
import { service } from '../src/config/axios/service'
service.defaults.adapter=async config=>({config,data:{code:0,data:[]},status:200,statusText:'OK',headers:{}})
const node=ref({id:'director',name:'编导审核',type:11,approveType:1,candidateStrategy:35,candidateParam:'7',approveMethod:1,rejectHandler:{type:1},assignEmptyHandler:{type:2},assignStartUserHandlerType:1,timeoutHandler:{enable:false},signEnable:false,reasonRequire:false})
const app=createApp({setup(){
  const config=ref<InstanceType<typeof UserTaskNodeConfig>>()
  provide('roleList',ref([{id:7,name:'编导',status:0},{id:9,name:'测试审核组',status:0}]))
  provide('formType',ref(20));provide('formFields',ref([]));provide('userList',ref([]));provide('deptList',ref([]));provide('postList',ref([]));provide('userGroupList',ref([]))
  const open=()=>{config.value?.showUserTaskNodeConfig(node.value);config.value?.openDrawer()}
  onMounted(open)
  Object.assign(window,{roleScopeFixture:{read:()=>node.value.candidateParam,reopen:open}})
  return()=>h('main',[h('button',{onClick:open},'编辑审核节点'),h(UserTaskNodeConfig,{ref:config,flowNode:node.value})])
}})
setupStore(app)
await setupI18n(app)
app.use(ElementPlus).mount('#app')
