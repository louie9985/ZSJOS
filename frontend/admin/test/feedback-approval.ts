// UTF-8. Actual Admin approval component with synthetic request transport.
import { createApp, h, ref } from 'vue'
import ElementPlus, { ElDialog } from 'element-plus'
import 'element-plus/dist/index.css'
import FeedbackApprovalPanel from '../src/views/zsjos/feedback/components/FeedbackApprovalPanel.vue'
import request from '@/config/axios'
import { feedbackUrgeTaskTarget, isFeedbackUrgeNotification } from '../src/utils/feedbackUrgeNotification'
const state = { fail: false }
Object.assign(window, { feedbackFixture: state, feedbackUrgeTaskTarget, isFeedbackUrgeNotification })
request.get = async ({ url, params }: { url?: string; params?: { roundNo?: number; id?: string } }) => {
  if (state.fail) throw new Error('流程读取失败，请重试')
  if (url === '/bpm/task/get-todo') {
    if (params?.id !== 'active-task') throw new Error('任务不存在或已转交')
    return { id: 'active-task', processInstance: { id: 'active-process' } } as never
  }
  const round = params?.roundNo || 2
  return { roundNo: round, latestRoundNo: 2, rounds: [{ roundNo: 1 }, { roundNo: 2 }], availability: 'AVAILABLE',
    fields: [], values: { title: round === 1 ? '第一轮原始内容' : '第二轮调整后的内容' },
    progress: { status: round === 1 ? 3 : 1, currentTasks: [{ name: '终审', assigneeName: '测试审批人乙' }],
      nodes: [{ id: 'review', name: '部门负责人审核', status: round === 1 ? 3 : 2, candidates: [], tasks: [
        { id: 'task-1', assigneeName: '测试审批人甲', createTime: Date.now() - 3600000, endTime: Date.now(), status: round === 1 ? 3 : 2, reason: round === 1 ? '请补充使用场景' : '同意' }
      ] }] } } as never
}
if (new URLSearchParams(location.search).has('message')) {
  const navigation = ref('')
  const warning = ref('')
  Object.assign(window, { ref, useRouter: () => ({ push: async (target: unknown) => { navigation.value = JSON.stringify(target) } }),
    useMessage: () => ({ warning: (text: string) => { warning.value = text } }) })
  const Detail = (await import('../src/views/system/notify/my/MyNotifyMessageDetail.vue')).default
  const detail = ref()
  Object.assign(window, { openUrgeMessage: (taskId: string) => detail.value.open({
    templateTitle: '需求审批催办', templateNickname: '中视简', templateContent: '请处理需求审批',
    actionType: 'business_detail', sceneCode: 'zsjos.feedback.approval_urged', templateParams: { taskId }
  }) })
  const app = createApp({ render: () => h('div', [h(Detail, { ref: detail }), h('p', { id: 'navigation' }, navigation.value), h('p', { id: 'warning' }, warning.value)]) })
  app.component('Dialog', { props: ['modelValue', 'title'], emits: ['update:modelValue'], setup: (props, { slots, emit }) => () => h(ElDialog,
    { modelValue: props.modelValue, title: props.title, width: 'min(600px, 96vw)', 'onUpdate:modelValue': value => emit('update:modelValue', value) }, slots) })
  app.component('DictTag', { render: () => h('span', '测试状态') })
  app.use(ElementPlus).mount('#app')
} else {
  createApp({ render: () => h(FeedbackApprovalPanel, { id: 1 }, { default: ({ values }: { values: Record<string, unknown> }) => h('p', String(values.title)) }) }).use(ElementPlus).mount('#app')
}
