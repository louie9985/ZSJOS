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
        </el-select>
      </el-form-item>
      <el-form-item v-if="formData.fieldType === FieldType.SELECT" label="下拉选项" prop="options">
        <div class="w-full">
          <div v-for="(_, index) in formData.options" :key="index" class="mb-2 flex gap-2">
            <el-input v-model="formData.options![index]" placeholder="请输入选项" />
            <el-button link type="danger" @click="removeOption(index)">删除</el-button>
          </div>
          <el-button type="primary" plain size="small" @click="addOption">
            <Icon icon="ep:plus" class="mr-5px" /> 添加选项
          </el-button>
        </div>
      </el-form-item>
      <el-form-item label="是否必填" prop="required">
        <el-switch v-model="formData.required" />
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

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')
const formData = ref<CategoryFieldApi.CategoryFieldVO>(buildEmptyForm())
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
    required: false,
    sort: 0
  }
}

const addOption = () => {
  formData.value.options = [...(formData.value.options || []), '']
}
const removeOption = (index: number) => {
  formData.value.options!.splice(index, 1)
}

const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = t('action.' + type)
  formType.value = type
  formData.value = buildEmptyForm()
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
  if (formData.value.fieldType === FieldType.SELECT) {
    const options = (formData.value.options || []).map((o) => o.trim()).filter(Boolean)
    if (options.length === 0) {
      message.warning('下拉选择类型至少需要一个选项')
      return
    }
    formData.value.options = options
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
