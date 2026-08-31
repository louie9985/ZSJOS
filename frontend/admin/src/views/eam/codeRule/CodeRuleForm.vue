<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle" width="560px">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="120px"
    >
      <el-form-item label="适用分类" prop="categoryId">
        <el-tree-select
          v-model="formData.categoryId"
          :data="categoryTree"
          :props="{ label: 'name', children: 'children', value: 'id' }"
          check-strictly
          node-key="id"
          clearable
          class="!w-full"
          placeholder="留空表示全局默认规则"
        />
      </el-form-item>
      <el-form-item label="固定前缀" prop="prefix">
        <el-input v-model="formData.prefix" placeholder="如 AS" />
      </el-form-item>
      <el-form-item label="拼接分类编码" prop="useCategoryCode">
        <el-switch v-model="formData.useCategoryCode" />
      </el-form-item>
      <el-form-item label="日期格式" prop="dateFormat">
        <el-select v-model="formData.dateFormat" clearable class="!w-full" placeholder="不含日期">
          <el-option label="年份（2026）" value="yyyy" />
          <el-option label="年月（202608）" value="yyyyMM" />
        </el-select>
      </el-form-item>
      <el-form-item label="流水号位数" prop="serialLength">
        <el-input-number
          v-model="formData.serialLength"
          :min="1"
          :max="12"
          :controls="false"
          class="!w-full"
        />
      </el-form-item>
      <el-form-item label="分隔符" prop="separator">
        <el-input v-model="formData.separator" maxlength="5" placeholder="默认 -" />
      </el-form-item>

      <el-form-item label="编号示例">
        <span class="font-mono text-blue-600">{{ preview }}</span>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import * as CodeRuleApi from '@/api/eam/codeRule'

defineOptions({ name: 'EamCodeRuleForm' })

defineProps<{ categoryTree: any[] }>()
const emit = defineEmits(['success'])

const { t } = useI18n()
const message = useMessage()

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')
const formData = ref<CodeRuleApi.CodeRuleVO>(buildEmptyForm())
const formRef = ref()

const formRules = reactive({
  useCategoryCode: [{ required: true, message: '请选择是否拼接分类编码', trigger: 'change' }],
  serialLength: [{ required: true, message: '流水号位数不能为空', trigger: 'blur' }]
})

function buildEmptyForm(): CodeRuleApi.CodeRuleVO {
  return {
    categoryId: undefined,
    prefix: '',
    useCategoryCode: true,
    dateFormat: 'yyyy',
    serialLength: 4,
    separator: '-'
  }
}

const preview = computed(() => {
  const sep = formData.value.separator || '-'
  const segments: string[] = []
  if (formData.value.prefix) {
    segments.push(formData.value.prefix)
  }
  if (formData.value.useCategoryCode) {
    segments.push('XX')
  }
  if (formData.value.dateFormat) {
    const now = new Date()
    const year = String(now.getFullYear())
    const month = String(now.getMonth() + 1).padStart(2, '0')
    segments.push(formData.value.dateFormat === 'yyyyMM' ? `${year}${month}` : year)
  }
  segments.push('1'.padStart(formData.value.serialLength ?? 4, '0'))
  return segments.join(sep)
})

const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  formData.value = buildEmptyForm()
  formRef.value?.resetFields()
  if (id) {
    formLoading.value = true
    try {
      formData.value = await CodeRuleApi.getCodeRule(id)
    } finally {
      formLoading.value = false
    }
  }
}
defineExpose({ open })

const submitForm = async () => {
  await formRef.value.validate()
  formLoading.value = true
  try {
    if (formType.value === 'create') {
      await CodeRuleApi.createCodeRule(formData.value)
      message.success(t('common.createSuccess'))
    } else {
      await CodeRuleApi.updateCodeRule(formData.value)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}
</script>
