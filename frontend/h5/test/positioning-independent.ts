import 'amfe-flexible'
import '../src/styles/base.css'
import '../src/styles/themes/coral.css'
import '../src/styles/vant-overrides.css'
// Isolated browser fixture; no business requests or account required.
import { createApp } from 'vue'
import axios from 'axios'
import Vant from 'vant'
import 'vant/lib/index.css'
axios.defaults.adapter=async config=>({config,status:200,statusText:'OK',headers:{},data:{code:0,data:config.method==='post'?true:{state:'ready',cardNo:'PC-TEST',serviceLabel:'营养课程服务',fields:[{key:'name',title:'账号名称建议'},{key:'material',title:'S1参考素材'},{key:'file',title:'采访稿全文'}],values:{name:'独立定位卡名称',material:[1],file:[2]},dictSnapshots:{material:[{titleSnapshot:'已保存素材标题'}],file:[{name:'采访稿.txt'}]}}}})
const capRoot=()=>{const root=document.documentElement; if(parseFloat(root.style.fontSize)>54)root.style.fontSize='54px'}
capRoot();window.addEventListener('resize',capRoot)
const { default: Confirmation } = await import('../src/pages/positioning/confirmation.vue')
createApp(Confirmation).use(Vant).mount('#app')
