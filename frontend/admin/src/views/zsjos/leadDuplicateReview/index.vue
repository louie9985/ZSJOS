<script setup lang="ts">
import WorkbenchListPage from '../components/WorkbenchListPage.vue'
import * as Api from '@/api/zsjos/workbenchMenus'
const message = useMessage()
const open = ref(false)
const current = ref<any>()
const form = reactive({
  resultType: 'allow_flow',
  opinion: ''
})
const saving = ref(false)
let reloadList = () => {}
const show = (row: any, fn: () => void) => {
  current.value = row
  reloadList = fn
  Object.assign(form, {
    resultType: 'allow_flow',
    opinion: ''
  })
  open.value = true
}
const submit = async () => {
  if (!form.opinion.trim()) return message.warning('请填写复核意见')
  saving.value = true
  try {
    await Api.decideDuplicateReview(current.value.id, {
      ...form,
      opinion: form.opinion.trim(),
      attachments: [],
      idempotencyKey: crypto.randomUUID()
    })
    message.success('复核结论已提交')
    open.value = false
    await reloadList()
  } finally {
    saving.value = false
  }
}
</script>
<template>
  <WorkbenchListPage
    title="重复客资复核"
    endpoint="/zsjos/lead-duplicate-review/page"
    description="重复客资复核队列"
    :query="{ status: 'pending' }"
    ><template #row-actions="{ row, reload }"
      ><el-button
        link
        type="primary"
        v-hasPermi="['zsjos:lead-duplicate-review:process']"
        @click="show(row, reload)"
        >复核</el-button
      ></template
    ></WorkbenchListPage
  ><el-dialog v-model="open" title="重复客资复核" width="560px"
    ><el-form :model="form" label-width="110px"
      ><el-form-item label="结论" required
        ><el-select v-model="form.resultType" class="w-100%"
          ><el-option label="放行，进入正式客资流程" value="allow_flow" /><el-option
            label="确认重复，关闭本次提交"
            value="close_duplicate" /></el-select></el-form-item
      ><el-alert
        v-if="form.resultType === 'allow_flow'"
        title="放行后将按原提交快照创建正式客资，并继续进入分配或销售跟进流程"
        type="info"
        :closable="false"
        class="mb-12px" />
      <el-alert
        v-if="form.resultType === 'close_duplicate'"
        title="确认关闭后不会创建客资，也不会进入分配或业绩统计"
        type="warning"
        :closable="false"
        class="mb-12px" />
      <el-form-item label="复核意见" required
        ><el-input
          v-model="form.opinion"
          type="textarea"
          :rows="4"
          maxlength="2000"
          show-word-limit /></el-form-item></el-form
    ><template #footer
      ><el-button @click="open = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="submit">提交</el-button></template
    ></el-dialog
  >
</template>
