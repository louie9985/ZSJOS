<template>
  <!-- 按分类的自定义字段定义动态渲染表单项；字段定义随分类切换而重新加载 -->
  <template v-for="field in fields" :key="field.fieldKey">
    <el-form-item :label="field.fieldName" :prop="`extFields.${field.fieldKey}`">
      <el-input
        v-if="field.fieldType === FieldType.TEXT"
        v-model="model[field.fieldKey]"
        :placeholder="`请输入${field.fieldName}`"
        clearable
      />
      <el-input
        v-else-if="field.fieldType === FieldType.TEXTAREA"
        v-model="model[field.fieldKey]"
        type="textarea"
        :rows="3"
        :placeholder="`请输入${field.fieldName}`"
      />
      <el-input-number
        v-else-if="field.fieldType === FieldType.NUMBER"
        v-model="model[field.fieldKey]"
        class="!w-full"
        :controls="false"
        :placeholder="`请输入${field.fieldName}`"
      />
      <el-date-picker
        v-else-if="field.fieldType === FieldType.DATE"
        v-model="model[field.fieldKey]"
        type="date"
        value-format="YYYY-MM-DD"
        class="!w-full"
        :placeholder="`请选择${field.fieldName}`"
      />
      <el-select
        v-else-if="field.fieldType === FieldType.SELECT"
        v-model="model[field.fieldKey]"
        class="!w-full"
        clearable
        :placeholder="`请选择${field.fieldName}`"
      >
        <el-option v-for="opt in selectOptions(field)" :key="String(opt.value)" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-upload
        v-else-if="field.fieldType === FieldType.FILE"
        action="/admin-api/infra/file/upload"
        :limit="1"
        :file-list="fileList(field.fieldKey)"
        :on-success="(res) => setFile(field.fieldKey, res.data)"
        :on-remove="() => setFile(field.fieldKey, '')"
      >
        <el-button><Icon icon="ep:upload" class="mr-5px" />上传文件</el-button>
      </el-upload>
    </el-form-item>
  </template>

  <el-empty
    v-if="!loading && fields.length === 0 && categoryId"
    description="该分类未配置自定义字段"
    :image-size="60"
  />
</template>

<script setup lang="ts">
import * as CategoryFieldApi from '@/api/eam/categoryField'
import { FieldType } from '@/api/eam/categoryField'
import { getStrDictOptions } from '@/utils/dict'

defineOptions({ name: 'EamDynamicFields' })

const props = defineProps<{
  /** 当前分类，切换时重新拉取字段定义 */
  categoryId?: number
  /** 扩展字段值对象，双向绑定 */
  modelValue: Record<string, any>
}>()
const emit = defineEmits<{ (e: 'update:modelValue', value: Record<string, any>): void }>()

const fields = ref<CategoryFieldApi.CategoryFieldVO[]>([])
const loading = ref(false)

const model = computed({
  get: () => props.modelValue || {},
  set: (val) => emit('update:modelValue', val)
})

const selectOptions = (field: CategoryFieldApi.CategoryFieldVO) =>
  field.optionSource === 'SYSTEM_DICT' && field.dictType
    ? getStrDictOptions(field.dictType)
    : (field.options || []).map((value) => ({ label: value, value }))
const fileList = (key: string) => model.value[key]
  ? [{ name: String(model.value[key]).split('/').pop() || '已上传文件', url: model.value[key] }]
  : []
const setFile = (key: string, value: string) => emit('update:modelValue', { ...model.value, [key]: value || undefined })

/** 分类切换后，丢弃不再属于新分类的扩展字段值，避免提交时被后端拒绝 */
const loadFields = async (categoryId?: number) => {
  if (!categoryId) {
    fields.value = []
    return
  }
  loading.value = true
  try {
    const definitions = await CategoryFieldApi.getEffectiveFieldList(categoryId)
    fields.value = definitions.filter((field) => field.adminVisible !== false)
    const allowed = new Set(fields.value.map((f) => f.fieldKey))
    const next: Record<string, any> = {}
    Object.entries(model.value).forEach(([key, value]) => {
      if (allowed.has(key)) {
        next[key] = value
      }
    })
    emit('update:modelValue', next)
  } finally {
    loading.value = false
  }
}

watch(() => props.categoryId, loadFields, { immediate: true })

defineExpose({ reload: () => loadFields(props.categoryId) })
</script>
