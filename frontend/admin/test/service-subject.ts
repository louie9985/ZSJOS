import {createApp,h} from 'vue'
import {createRouter,createWebHistory} from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import Page from '../src/views/zsjos/my-students.vue'
const router=createRouter({history:createWebHistory(),routes:[{path:'/test/service-subject.html',component:Page}]})
const app=createApp(Page)
app.component('ContentWrap',{render(){return h('div',this.$slots.default?.())}})
app.component('Pagination',{render(){return null}})
app.component('Icon',{render(){return null}})
app.use(ElementPlus).use(router)
await router.isReady();app.mount('#app')
