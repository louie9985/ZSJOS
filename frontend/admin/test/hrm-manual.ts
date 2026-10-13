import { createApp, h, ref } from 'vue'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import '../src/plugins/unocss'
import { service } from '../src/config/axios/service'
import { setupStore, store } from '../src/store'
import { setActivePinia } from 'pinia'
import { setupI18n } from '../src/plugins/vueI18n'
setActivePinia(store)
const employee = {
  id: 1,
  name: '合成员工',
  mobile: '13800000001',
  jobNumber: 'TEST-01',
  type: 1,
  status: 2,
  entryStatus: 1,
  probation: 3,
  entryTime: Date.now() - 100 * 86400000,
  version: 0,
  deptId: 10,
  regularTime: null,
  leaveTime: null
}
const calls: unknown[] = []
const mode = new URLSearchParams(location.search).get('view')
const rule = () => ({
  enabled: false,
  advanceDays: 1,
  triggerTime: '09:00',
  deptIds: [],
  includeChildDepartments: false,
  recipientUserIds: [],
  recipientDeptIds: []
})
const reminderConfig = { regular: rule(), leave: rule(), change: rule() }
const pendingPlan = {
  id: 31,
  employeeId: 1,
  type: 4,
  state: 'PENDING',
  version: 0,
  changeMode: 'PLAN',
  plannedTime: Date.now() + 86400000,
  beforeSnapshot: JSON.stringify({ type: 1, status: 2, entryStatus: 1, probation: 3, deptId: 10 }),
  afterSnapshot: JSON.stringify({ ...employee, status: 1 }),
  remark: '合成转正计划'
}
const dictionaries = [
  ['hrm_employee_type', ['正式', '非正式']],
  ['hrm_employee_status', ['正式', '试用', '实习', '兼职', '劳务', '顾问', '返聘', '外包']],
  ['hrm_employee_entry_status', ['在职', '待入职', '待离职', '离职']]
].flatMap(([type, labels]) =>
  (labels as string[]).map((label, i) => ({ dictType: type, value: String(i + 1), label }))
)
service.defaults.adapter = async (config) => {
  const path = config.url || ''
  let data: unknown
  if (location.search.includes('failed') && path.includes('/employee/get'))
    throw new Error('合成加载失败')
  if (path.includes('/lifecycle-config/preview'))
    data = {
      regular: { recipientUserIds: [7], pendingCount: 1 },
      leave: { recipientUserIds: [], pendingCount: 0 },
      change: { recipientUserIds: [], pendingCount: 0 }
    }
  else if (path.includes('/lifecycle-config') && config.method === 'put') {
    calls.push(JSON.parse(String(config.data)))
    data = true
  } else if (path.includes('/lifecycle-config')) data = reminderConfig
  else if (path.includes('/change-record/cancel')) {
    calls.push(JSON.parse(String(config.data)))
    data = true
  } else if (path.includes('/change-record/list')) data = [pendingPlan]
  else if (path.includes('/change-record/command-options'))
    data = [
      { value: 4, label: '转正' },
      { value: 11, label: '离职' },
      { value: 12, label: '人员变更' }
    ]
  else if (path.includes('/employee/get')) data = employee
  else if (path.includes('/employee/update')) {
    const value = JSON.parse(String(config.data))
    calls.push(value)
    Object.assign(employee, value, { version: employee.version + 1 })
    data = true
  } else if (path.includes('/employee/config/create-field/list')) data = []
  else if (path.includes('/system/dict-data/simple-list')) {
    if (mode === 'labels') await new Promise((resolve) => setTimeout(resolve, 250))
    data = dictionaries
  } else if (path.includes('/system/dept/simple-list'))
    data = [{ id: 10, name: '合成部门', parentId: 0 }]
  else if (path.includes('/system/user/simple-list'))
    data = [{ id: 7, nickname: '合成人事', deptId: 10 }]
  else if (path.includes('simple-list') || path.includes('/list')) data = []
  else if (path.includes('/page')) data = { list: [], total: 0 }
  else throw new Error('Unexpected isolated request ' + path)
  return {
    config,
    request: { responseType: 'json' },
    status: 200,
    statusText: 'OK',
    headers: {},
    data: { code: 0, data }
  }
}
const { useUserStore } = await import('../src/store/modules/user')
useUserStore().permissions = new Set(['*:*:*'])
const { useDictStore } = await import('../src/store/modules/dict')
if (mode !== 'labels') await useDictStore().setDictMap()
const { default: EmployeeForm } = await import('../src/views/hrm/employee/EmployeeForm.vue')
const { default: Plans } =
  await import('../src/views/hrm/employee/detail/EmployeeChangeRecordList.vue')
const { default: Reminders } =
  await import('../src/views/hrm/birthday-care/LifecycleReminderConfig.vue')
const { default: Dialog } = await import('../src/components/Dialog/src/Dialog.vue')
const { Icon } = await import('../src/components/Icon')
const { default: Pagination } = await import('../src/components/Pagination/index.vue')
const { DictTag } = await import('../src/components/DictTag')
const { hasPermi } = await import('../src/directives/permission/hasPermi')
const { default: EmployeeLabel } =
  await import('../src/views/hrm/employee/components/HrmEmployeeDictionaryLabel.vue')
const labelValue = ref(1)
const labelCases = [
  { id: 'type', dictType: 'hrm_employee_type', value: 1 },
  { id: 'status', dictType: 'hrm_employee_status', value: 1 },
  { id: 'entry', dictType: 'hrm_employee_entry_status', value: 1 },
  { id: 'snapshot', dictType: 'hrm_employee_status', value: 1, snapshotLabel: '记录时的名称' },
  { id: 'unknown', dictType: 'hrm_employee_status', value: 999 },
  { id: 'empty', dictType: 'hrm_employee_status', value: null }
]
Object.assign(window, {
  setLabelValue: (value: number) => {
    labelValue.value = value
  }
})
const app = createApp({
  render: () =>
    mode === 'labels'
      ? h('div', { style: 'display:grid;gap:20px;padding:24px' }, [
          ...labelCases.map((item) =>
            h('div', { 'data-testid': item.id }, [h(EmployeeLabel, item)])
          ),
          h('div', { 'data-testid': 'reactive' }, [
            h(EmployeeLabel, { dictType: 'hrm_employee_status', value: labelValue.value })
          ])
        ])
      : mode === 'plans'
        ? h(Plans, { employee, employeeId: 1 })
        : mode === 'reminders'
          ? h(Reminders)
          : h(EmployeeForm, { ref: 'employee' })
})
setupStore(app)
await setupI18n(app)
app.use(ElementPlus)
hasPermi(app)
app.use(createRouter({ history: createMemoryHistory(), routes: [] }))
app.component('Dialog', Dialog)
app.component('Icon', Icon)
app.component('Pagination', Pagination)
app.component('DictTag', DictTag)
app.component('ContentWrap', {
  render() {
    return h('section', this.$slots.default?.())
  }
})
const vm = app.mount('#app')
Object.assign(window, {
  hrmCalls: calls,
  openHrm: () =>
    (vm.$refs.employee as { open: (type: string, id: number) => Promise<void> }).open('update', 1)
})
if (!mode) await (window as unknown as { openHrm: () => Promise<void> }).openHrm()
