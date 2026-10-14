import { createApp, h } from 'vue'
import { createRouter, createWebHistory, RouterView } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import CourseCalendar from '../src/views/zsjos/courseCalendar/index.vue'
import { setupI18n } from '../src/plugins/vueI18n'
import { setupStore } from '../src/store'
import { service } from '../src/config/axios/service'
import { openCourseNotification } from '../src/utils/courseNotification'
const state = { fail: false, requests: [] as string[] }
Object.assign(window, { courseLinkFixture: state })
const rows=[{id:7,courseName:'通知课程',courseFormValue:'LIVE',courseFormLabelSnapshot:'直播',startTime:'2027-02-03T09:00:00',endTime:'2027-02-03T10:00:00',remark:'课程备注',attachmentIds:[]}]
service.defaults.adapter=async config=>{ state.requests.push(config.url || ''); if(state.fail && config.url?.endsWith('/7')) throw new Error('课程已删除或不可访问'); return ({config,request:{responseType:'json'},data:{code:0,data:config.url?.endsWith('/7')?rows[0]:config.url?.endsWith('/page')?rows:[]},status:200,statusText:'OK',headers:{}}) }
const router=createRouter({history:createWebHistory(),routes:[{path:'/test/review-course-link.html',name:'ZsjosCourseCalendar',component:CourseCalendar}]})
Object.assign(state, { go: (id:number) => router.push({query:{courseId:String(id)}}) })
const app=createApp({setup(){return()=>h('main',[h('button',{onClick:()=>openCourseNotification(router,{actionType:'business_detail',bizType:'calendar',sceneCode:'zsjos.calendar.course',bizId:7} as any)},'打开课程通知'),h(RouterView)])}})
setupStore(app);await setupI18n(app);app.use(ElementPlus);app.use(router);await router.isReady();app.mount('#app')
