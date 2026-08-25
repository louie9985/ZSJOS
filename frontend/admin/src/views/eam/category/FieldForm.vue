<template>
  <Dialog v-model="dialogVisible" :title="dialogTitle">
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="100px"
    >
      <el-form-item label="字段名称" prop="fieldName">
        <el-input v-model="formData.fieldName" placeholder="展示给使用者的名称，如 账号" />
      </el-form-item>
      <el-form-item label="字段标识" prop="fieldKey">
        <el-input
          v-model="formData.fieldKey"
          :disabled="formType === 'update'"
          placeholder="英文标识，如 account；创建后不可修改"
        />
      </el-form-item>
      <el-form-item label="字段类型" prop="fieldType">
        <el-select v-model="formData.fieldType" class="!w-full" placeholder="请选择字段类型">
          <el-option label="单行文本" :value="FieldType.TEXT" />
          <el-option label="多行文本" :value="FieldType.TEXTAREA" />
          <el-option label="数字" :value="FieldType.NUMBER" />
          <el-option label="日期" :value="FieldType.DATE" />
          <el-option label="下拉选择" :value="FieldType.SELECT" />
          <el-option label="图片/文件" :value="FieldType.FILE" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="formData.fieldType === FieldType.SELECT" label="选项来源" prop="optionSource">
        <el-select v-model="formData.optionSource" class="!w-full">
          <el-option label="系统字典" value="SYSTEM_DICT" />
          <el-option label="固定选项" value="STATIC" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="formData.fieldType === FieldType.SELECT && formData.optionSource === 'SYSTEM_DICT'" label="字典类型" prop="dictType">
        <el-input v-model="formData.dictType" placeholder="请输入 System 字典类型编码" />
      </el-form-item>
      <el-form-item v-if="formData.fieldType === FieldType.SELECT && formData.optionSource === 'STATIC'" label="下拉选项" prop="options">
        <el-select v-model="formData.options" multiple filterable allow-create default-first-option class="!w-full" />
      </el-form-item>
      <el-form-item label="管理端显示" prop="adminVisible">
        <el-switch v-model="formData.adminVisible" />
        <span class="ml-2 text-xs text-gray-500">管理端显示时仍为选填</span>
      </el-form-item>
      <el-form-item label="员工表显示" prop="collectionVisible">
        <el-switch v-model="formData.collectionVisible" />
      </el-form-item>
      <el-form-item label="员工表必填" prop="collectionRequired">
        <el-switch v-model="formData.collectionRequired" :disabled="!formData.collectionVisible" />
      </el-form-item>
      <el-form-item label="条件规则" prop="conditionRule">
        <el-input
          v-model="conditionRuleText"
          type="textarea"
          :rows="3"
          :placeholder="conditionRulePlaceholder"
        />
      </el-form-item>
      <el-form-item label="排序" prop="sort">
        <el-input-number v-model="formData.sort" :min="0" class="!w-full" :controls="false" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="formLoading" type="primary" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import * as CategoryFieldApi from '@/api/eam/categoryField'
import { FieldType } from '@/api/eam/categoryField'

defineOptions({ name: 'EamCategoryFieldForm' })

const props = defineProps<{ categoryId?: number }>()
const emit = defineEmits(['success'])

const { t } = useI18n()
const message = useMessage()
const conditionRulePlaceholder = '可选 JSON，例如 {"field":"ownership","equals":"公司资产"}'

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')
const formData = ref<CategoryFieldApi.CategoryFieldVO>(buildEmptyForm())
const conditionRuleText = ref('')
const formRef = ref()

const formRules = reactive({
  fieldName: [{ required: true, message: '字段名称不能为空', trigger: 'blur' }],
  fieldKey: [
    { required: true, message: '字段标识不能为空', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/,
      message: '字段标识需以字母开头，仅含字母、数字和下划线',
      trigger: 'blur'
    }
  ],
  fieldType: [{ required: true, message: '字段类型不能为空', trigger: 'change' }],
  sort: [{ required: true, message: '排序不能为空', trigger: 'blur' }]
})

function buildEmptyForm(): CategoryFieldApi.CategoryFieldVO {
  return {
    categoryId: props.categoryId ?? 0,
    fieldKey: '',
    fieldName: '',
    fieldType: FieldType.TEXT,
    options: [],
    optionSource: 'SYSTEM_DICT',
    dictType: '',
    required: false,
    adminVisible: true,
    collectionVisible: true,
    collectionRequired: false,
    sort: 0
  }
}

const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  formData.value = buildEmptyForm()
  conditionRuleText.value = ''
  formRef.value?.resetFields()
  if (id) {
    formLoading.value = true
    try {
      const list: CategoryFieldApi.CategoryFieldVO[] = await CategoryFieldApi.getFieldList(
        props.categoryId!
      )
      const target = list.find((item) => item.id === id)
      if (target) {
        formData.value = { ...target, options: target.options || [] }
        conditionRuleText.value = target.conditionRule
          ? JSON.stringify(target.conditionRule, null, 2)
          : ''
      }
    } finally {
      formLoading.value = false
    }
  }
}
defineExpose({ open })

const submitForm = async () => {
  await formRef.value.validate()
  // 下拉类型必须至少有一个非空选项，否则该字段在资产表单上无法选值
  if (formData.value.fieldType === FieldType.SELECT && formData.value.optionSource === 'STATIC') {
    const options = (formData.value.options || []).map((o) => o.trim()).filter(Boolean)
    if (options.length === 0) {
      message.warning('下拉选择类型至少需要一个选项')
      return
    }
    formData.value.options = options
  }
  if (formData.value.fieldType === FieldType.SELECT && formData.value.optionSource === 'SYSTEM_DICT' && !formData.value.dictType?.trim()) {
    message.warning('请选择或填写系统字典类型')
    return
  }
  if (conditionRuleText.value.trim()) {
    try {
      const rule = JSON.parse(conditionRuleText.value)
      if (!rule || Array.isArray(rule) || typeof rule !== 'object') {
        message.warning('条件规则必须是 JSON 对象')
        return
      }
      formData.value.conditionRule = rule
    } catch {
      message.warning('条件规则不是有效的 JSON')
      return
    }
  } else {
    formData.value.conditionRule = undefined
  }
  formData.value.required = false
  if (!formData.value.collectionVisible) {
    formData.value.collectionRequired = false
  }

  formLoading.value = true
  try {
    const data = { ...formData.value, categoryId: props.categoryId! }
    if (formType.value === 'create') {
      await CategoryFieldApi.createField(data)
      message.success(t('common.createSuccess'))
    } else {
      await CategoryFieldApi.updateField(data)
      message.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}
</script>
