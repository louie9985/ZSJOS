<template>
  <ContentWrap title="人事到期提醒">
    <el-alert
      type="info"
      :closable="false"
      title="提前一次、到期一次，仅发送消息。员工状态和岗位由人工维护；企业微信可在统一消息规则中配置。"
      class="mb-16px"
    />
    <el-alert v-if="error" type="error" :title="error" :closable="false"
      ><el-button link type="primary" @click="load">重试</el-button></el-alert
    >
    <el-form
      class="lifecycle-reminder-form"
      v-loading="loading"
      :disabled="loading || !!error"
      label-width="140px"
    >
      <el-card v-for="item in items" :key="item.key" shadow="never" class="mb-16px">
        <template #header>{{ item.label }}</template>
        <el-form-item label="启用提醒"><el-switch v-model="form[item.key].enabled" /></el-form-item>
        <el-form-item label="提前天数"
          ><el-input-number v-model="form[item.key].advanceDays" :min="0" :max="30"
        /></el-form-item>
        <el-form-item label="发送时刻"
          ><el-time-picker
            v-model="form[item.key].triggerTime"
            format="HH:mm"
            value-format="HH:mm"
            :clearable="false"
          /><span class="ml-8px">北京时间</span></el-form-item
        >
        <el-form-item label="关注员工部门"
          ><DeptSelect v-model="form[item.key].deptIds" multiple class="w-full"
        /></el-form-item>
        <el-form-item label="包含下级部门"
          ><el-switch v-model="form[item.key].includeChildDepartments"
        /></el-form-item>
        <el-form-item label="接收账号"
          ><el-select
            v-model="form[item.key].recipientUserIds"
            placeholder="请选择接收账号"
            multiple
            filterable
            class="w-full"
            ><el-option
              v-for="user in users"
              :key="user.id"
              :label="user.nickname"
              :value="user.id" /></el-select
        ></el-form-item>
        <el-form-item label="接收部门"
          ><DeptSelect v-model="form[item.key].recipientDeptIds" multiple class="w-full"
        /></el-form-item>
        <el-alert
          v-if="preview"
          :closable="false"
          type="info"
          :title="`当前接收 ${preview[item.key].recipientUserIds.length} 人，待处理 ${preview[item.key].pendingCount} 项`"
        />
      </el-card>
      <el-button :loading="previewing" @click="showPreview">预览接收人和事项数量</el-button>
      <el-button
        v-hasPermi="['hrm:employee-reminder-config:update']"
        type="primary"
        :loading="saving"
        @click="save"
        >保存人事提醒配置</el-button
      >
    </el-form>
  </ContentWrap>
</template>
<script setup lang="ts">
import * as Api from '@/api/hrm/employee/lifecycleReminder'
import * as UserApi from '@/api/system/user'
import DeptSelect from '@/views/system/dept/components/DeptSelect.vue'
const message = useMessage()
const defaults = (): Api.LifecycleReminderRule => ({
  enabled: false,
  advanceDays: 1,
  triggerTime: '09:00',
  deptIds: [],
  includeChildDepartments: false,
  recipientUserIds: [],
  recipientDeptIds: []
})
const form = reactive<Api.LifecycleReminderConfig>({
  regular: defaults(),
  leave: defaults(),
  change: defaults()
})
const items = [
  { key: 'regular', label: '计划转正' },
  { key: 'leave', label: '计划离职' },
  { key: 'change', label: '其他人事异动' }
] as const
const loading = ref(false),
  saving = ref(false),
  previewing = ref(false),
  error = ref('')
const users = ref<UserApi.UserVO[]>([])
const preview = ref<Awaited<ReturnType<typeof Api.previewConfig>>>()
watch(
  form,
  () => {
    preview.value = undefined
  },
  { deep: true }
)
async function load() {
  loading.value = true
  error.value = ''
  try {
    const [value, accounts] = await Promise.all([Api.getConfig(), UserApi.getSimpleUserList()])
    Object.assign(form, value)
    users.value = accounts
  } catch (e) {
    error.value = e instanceof Error ? e.message : '人事提醒配置加载失败'
  } finally {
    loading.value = false
  }
}
async function showPreview() {
  previewing.value = true
  try {
    preview.value = await Api.previewConfig(form)
  } finally {
    previewing.value = false
  }
}
async function save() {
  saving.value = true
  try {
    await showPreview()
    await Api.saveConfig(form)
    message.success('人事提醒配置已保存')
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>

<style scoped>
@media (max-width: 600px) {
  .lifecycle-reminder-form :deep(.el-form-item) {
    display: block;
  }
  .lifecycle-reminder-form :deep(.el-form-item__label) {
    width: auto !important;
    justify-content: flex-start;
  }
  .lifecycle-reminder-form :deep(.el-form-item__content) {
    margin-left: 0 !important;
  }
  .lifecycle-reminder-form :deep(.el-date-editor) {
    width: 100%;
  }
}
</style>
