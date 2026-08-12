<template>
  <ContentWrap>
    <div class="rule-heading">
      <div>
        <h3>客资派单规则</h3>
        <p>自动派单仅轮询页面在线且已开启接单的销售；三圈无人可接时进入抢单池。</p>
      </div>
      <el-tag type="success" effect="plain">启用中</el-tag>
    </div>
  </ContentWrap>

  <ContentWrap v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="mb-18px">
      <template #default
        ><el-button link type="primary" @click="loadRule">重新加载</el-button></template
      >
    </el-alert>
    <el-form
      v-else
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-width="150px"
      class="rule-form"
    >
      <el-form-item label="当前策略">
        <el-input model-value="全公司在线轮询" disabled />
      </el-form-item>
      <el-form-item label="候选扫描">
        <el-input model-value="每次最多轮询 3 圈；跳过离线、暂停和已有待接单销售" disabled />
      </el-form-item>
      <el-form-item label="接单超时" prop="acceptTimeoutSeconds">
        <el-input-number v-model="formData.acceptTimeoutSeconds" :min="10" :max="3600" :step="10" />
        <span class="unit">秒</span>
      </el-form-item>
      <el-form-item label="最大尝试次数" prop="maxAttempts">
        <el-input-number v-model="formData.maxAttempts" :min="1" :max="20" />
        <span class="unit">次</span>
      </el-form-item>
      <el-form-item>
        <ZsjosPopconfirm
          action="保存客资派单规则"
          v-model:visible="confirmVisible"
          @confirm="saveRule"
        >
          <el-button
            type="primary"
            :loading="saving"
            v-hasPermi="['zsjos:lead-rule:update']"
            @click="prepareSave"
          >
            <Icon icon="ep:check" class="mr-5px" />保存规则
          </el-button>
        </ZsjosPopconfirm>
      </el-form-item>
    </el-form>
  </ContentWrap>
</template>

<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus'
import * as LeadRuleApi from '@/api/zsjos/leadRule'
import ZsjosPopconfirm from '../components/ZsjosPopconfirm.vue'

defineOptions({ name: 'ZsjosLeadRule' })

const message = useMessage()
const loading = ref(true)
const saving = ref(false)
const confirmVisible = ref(false)
const error = ref('')
const formRef = ref<FormInstance>()
const formData = reactive<LeadRuleApi.LeadAssignmentRuleUpdateReqVO>({
  acceptTimeoutSeconds: 120,
  maxAttempts: 5
})
const rules: FormRules = {
  acceptTimeoutSeconds: [
    { required: true, message: '请输入接单超时秒数', trigger: 'blur' },
    { type: 'number', min: 10, max: 3600, message: '范围为 10–3600 秒', trigger: 'change' }
  ],
  maxAttempts: [
    { required: true, message: '请输入最大尝试次数', trigger: 'blur' },
    { type: 'number', min: 1, max: 20, message: '范围为 1–20 次', trigger: 'change' }
  ]
}

const loadRule = async () => {
  loading.value = true
  error.value = ''
  try {
    const rule = await LeadRuleApi.getRule()
    formData.acceptTimeoutSeconds = rule.acceptTimeoutSeconds
    formData.maxAttempts = rule.maxAttempts
  } catch (loadError: any) {
    error.value = loadError?.msg || loadError?.message || '派单规则加载失败'
  } finally {
    loading.value = false
  }
}

const saveRule = async () => {
  confirmVisible.value = false
  saving.value = true
  try {
    await LeadRuleApi.updateRule({ ...formData })
    message.success('派单规则已更新，新提交客资将使用新规则')
    await loadRule()
  } finally {
    saving.value = false
  }
}

const prepareSave = async () => {
  await formRef.value?.validate()
  confirmVisible.value = true
}

onMounted(loadRule)
</script>

<style scoped>
.rule-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.rule-heading h3 {
  margin: 0 0 8px;
  font-size: 18px;
}

.rule-heading p {
  margin: 0;
  color: var(--el-text-color-secondary);
}

.rule-form {
  max-width: 620px;
  padding: 12px 0;
}

.rule-form :deep(.el-input),
.rule-form :deep(.el-input-number) {
  width: 280px;
}

.unit {
  margin-left: 10px;
  color: var(--el-text-color-secondary);
}

@media (width <= 768px) {
  .rule-heading {
    flex-direction: column;
  }

  .rule-form :deep(.el-input),
  .rule-form :deep(.el-input-number) {
    width: 100%;
  }
}
</style>
