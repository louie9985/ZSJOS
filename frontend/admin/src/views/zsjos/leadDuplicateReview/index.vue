<script setup lang="ts">
import WorkbenchListPage from '../components/WorkbenchListPage.vue'
import * as Api from '@/api/zsjos/workbenchMenus'
const message = useMessage()
const open = ref(false)
const current = ref<any>()
const form = reactive({
  resultType: 'new_person',
  matchedPersonId: undefined as number | undefined,
  matchedLeadId: undefined as number | undefined,
  selectedSalesUserId: undefined as number | undefined,
  opinion: ''
})
const saving = ref(false)
let reloadList = () => {}
const show = (row: any, fn: () => void) => {
  current.value = row
  reloadList = fn
  Object.assign(form, {
    resultType: 'new_person',
    matchedPersonId: undefined,
    matchedLeadId: undefined,
    selectedSalesUserId: undefined,
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
      ><el-form-item label="结论"
        ><el-select v-model="form.resultType" class="w-100%"
          ><el-option label="新建客户" value="new_person" /><el-option
            label="复用客户"
            value="reuse_person" /><el-option
            label="重新激活客资"
            value="reactivate_lead" /><el-option
            label="通知原负责人"
            value="notify_owner" /></el-select></el-form-item
      ><el-form-item v-if="form.resultType === 'reuse_person'" label="客户编号"
        ><el-input-number v-model="form.matchedPersonId" :min="1" /></el-form-item
      ><el-form-item
        v-if="['reactivate_lead', 'notify_owner'].includes(form.resultType)"
        label="客资编号"
        ><el-input-number v-model="form.matchedLeadId" :min="1" /></el-form-item
      ><el-form-item label="销售编号"
        ><el-input-number v-model="form.selectedSalesUserId" :min="1" /></el-form-item
      ><el-form-item label="复核意见"
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
