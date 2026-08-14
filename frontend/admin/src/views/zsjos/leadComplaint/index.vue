<script setup lang="ts">
import WorkbenchListPage from '../components/WorkbenchListPage.vue'
import * as Api from '@/api/zsjos/workbenchMenus'
const message = useMessage()
const open = ref(false)
const current = ref<any>()
const result = ref('founded')
const opinion = ref('')
const saving = ref(false)
let reloadList = () => {}
const show = (row: any, fn: () => void) => {
  current.value = row
  reloadList = fn
  result.value = 'founded'
  opinion.value = ''
  open.value = true
}
const submit = async () => {
  if (!opinion.value.trim()) return message.warning('请填写处理意见')
  saving.value = true
  try {
    await Api.decideComplaint(current.value.id, {
      result: result.value,
      opinion: opinion.value.trim(),
      evidenceFileIds: [],
      idempotencyKey: crypto.randomUUID()
    })
    message.success('投诉结论已提交')
    open.value = false
    await reloadList()
  } finally {
    saving.value = false
  }
}
</script>
<template>
  <WorkbenchListPage
    title="销售投诉处理"
    endpoint="/zsjos/lead-complaint/page"
    description="销售投诉处理队列"
    :query="{ status: 'pending' }"
    ><template #row-actions="{ row, reload }"
      ><el-button
        link
        type="primary"
        v-hasPermi="['zsjos:lead-complaint:handle']"
        @click="show(row, reload)"
        >处理</el-button
      ></template
    ></WorkbenchListPage
  ><el-dialog v-model="open" title="处理销售投诉" width="520px"
    ><el-form label-width="90px"
      ><el-form-item label="结论" required
        ><el-radio-group v-model="result"
          ><el-radio value="founded">成立</el-radio
          ><el-radio value="unfounded">不成立</el-radio></el-radio-group
        ></el-form-item
      ><el-form-item label="处理意见" required
        ><el-input
          v-model="opinion"
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
