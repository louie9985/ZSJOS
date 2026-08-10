<template>
  <ContentWrap>
    <div class="rule-heading">
      <div>
        <h3>客资跟进规则</h3>
        <p>规则在销售取得客资归属时生效，已创建的首次跟进任务继续使用原有截止时间。</p>
      </div>
      <el-tag type="success" effect="plain">启用中</el-tag>
    </div>
  </ContentWrap>

  <ContentWrap v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="mb-18px">
      <template #default><el-button link type="primary" @click="loadRule">重新加载</el-button></template>
    </el-alert>
    <el-form v-else ref="formRef" :model="formData" :rules="rules" label-width="170px" class="rule-form">
      <el-form-item label="首次跟进时限" prop="firstFollowUpTimeoutMinutes">
        <el-input-number v-model="formData.firstFollowUpTimeoutMinutes" :min="5" :max="10080" :step="30" />
        <span class="unit">分钟</span>
      </el-form-item>
      <el-form-item label="有效性判定时限" prop="qualificationTimeoutMinutes">
        <el-input-number v-model="formData.qualificationTimeoutMinutes" :min="5" :max="43200" :step="30" />
        <span class="unit">分钟</span>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" v-hasPermi="['zsjos:lead-follow-up-rule:update']" @click="saveRule">
          <Icon icon="ep:check" class="mr-5px" />保存规则
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>
</template>

<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus'
import * as FollowUpRuleApi from '@/api/zsjos/leadFollowUpRule'

defineOptions({ name: 'ZsjosLeadFollowUpRule' })

const message = useMessage()
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const formRef = ref<FormInstance>()
const formData = reactive<FollowUpRuleApi.LeadFollowUpRuleUpdateReqVO>({ firstFollowUpTimeoutMinutes: 1440, qualificationTimeoutMinutes: 4320 })
const rules: FormRules = {
  firstFollowUpTimeoutMinutes: [
    { required: true, message: '请输入首次跟进时限', trigger: 'blur' },
    { type: 'number', min: 5, max: 10080, message: '范围为 5–10080 分钟', trigger: 'change' }
  ],
  qualificationTimeoutMinutes: [
    { required: true, message: '请输入有效性判定时限', trigger: 'blur' },
    { type: 'number', min: 5, max: 43200, message: '范围为 5–43200 分钟', trigger: 'change' }
  ]
}

const loadRule = async () => {
  loading.value = true
  error.value = ''
  try {
    const rule = await FollowUpRuleApi.getRule()
    formData.firstFollowUpTimeoutMinutes = rule.firstFollowUpTimeoutMinutes
    formData.qualificationTimeoutMinutes = rule.qualificationTimeoutMinutes
  } catch (loadError: any) {
    error.value = loadError?.msg || loadError?.message || '跟进规则加载失败'
  } finally {
    loading.value = false
  }
}

const saveRule = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    await FollowUpRuleApi.updateRule({ ...formData })
    message.success('跟进规则已更新，新的归属将使用新时限')
    await loadRule()
  } finally {
    saving.value = false
  }
}

onMounted(loadRule)
</script>

<style scoped>
.rule-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.rule-heading h3 { margin: 0 0 8px; font-size: 18px; }
.rule-heading p { margin: 0; color: var(--el-text-color-secondary); }
.rule-form { max-width: 620px; padding: 12px 0; }
.rule-form :deep(.el-input-number) { width: 280px; }
.unit { margin-left: 10px; color: var(--el-text-color-secondary); }
@media (width <= 768px) {
  .rule-heading { flex-direction: column; }
  .rule-form :deep(.el-input-number) { width: 100%; }
}
</style>
