<script setup lang="ts">
import WorkbenchListPage from '../components/WorkbenchListPage.vue'
import * as Api from '@/api/zsjos/workbenchMenus'
const message = useMessage()
const open = ref(false)
const current = ref<any>()
const decision = ref<'overturn' | 'uphold'>('uphold')
const reason = ref('')
const saving = ref(false)
let reloadList = () => {}
const show = (row: any, fn: () => void) => {
  current.value = row
  reloadList = fn
  decision.value = 'uphold'
  reason.value = ''
  open.value = true
}
const submit = async () => {
  if (!current.value?.taskId) return message.warning('当前申诉没有可处理任务，请刷新')
  if (!reason.value.trim()) return message.warning('请填写处理原因')
  saving.value = true
  try {
    await Api.decideAppeal(current.value.id, decision.value, {
      taskId: current.value.taskId,
      reason: reason.value.trim(),
      attachments: [],
      idempotencyKey: crypto.randomUUID()
    })
    message.success('申诉结论已提交')
    open.value = false
    await reloadList()
  } finally {
    saving.value = false
  }
}
</script>
<template>
  <WorkbenchListPage
    title="申诉处理"
    endpoint="/zsjos/lead/appeal/inbox-page"
    description="客资申诉处理队列"
    :query="{ handled: false }"
    advanced-scene="lead_appeal"
    advanced-search-endpoint="/zsjos/lead/appeal/inbox/search-page"
    advanced-placeholder="客资编号 / 姓名 / 手机号"
  ><template #row-actions="{ row, reload }"
      ><el-button link type="primary" :disabled="!row.taskId" @click="show(row, reload)"
        >处理</el-button
      ></template
    ></WorkbenchListPage
  ><el-dialog v-model="open" title="处理客资申诉" width="520px"
    ><el-form label-width="90px"
      ><el-form-item label="处理结论" required
        ><el-radio-group v-model="decision"
          ><el-radio value="overturn">撤销原判</el-radio
          ><el-radio value="uphold">维持原判</el-radio></el-radio-group
        ></el-form-item
      ><el-form-item label="处理原因" required
        ><el-input
          v-model="reason"
          type="textarea"
          :rows="4"
          maxlength="1000"
          show-word-limit /></el-form-item></el-form
    ><template #footer
      ><el-button @click="open = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="submit">提交</el-button></template
    ></el-dialog
  >
</template>
