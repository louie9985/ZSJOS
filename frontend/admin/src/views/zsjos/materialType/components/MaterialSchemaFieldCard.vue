<template>
  <div class="schema-field-card">
    <div class="schema-field-card__header">
      <strong>{{ index + 1 }}. {{ field.label || field.key || '未命名字段' }}</strong>
      <el-space>
        <el-button link :disabled="index === 0" @click="$emit('move', -1)">上移</el-button>
        <el-button link :disabled="index === total - 1" @click="$emit('move', 1)">下移</el-button>
        <el-button link type="danger" @click="$emit('remove')">删除</el-button>
      </el-space>
    </div>

    <el-row :gutter="12">
      <el-col :xs="24" :sm="12" :lg="6">
        <el-form-item label="字段编码" required>
          <el-input v-model="field.key" placeholder="如 title_hook" />
        </el-form-item>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-form-item label="字段名称" required>
          <el-input v-model="field.label" maxlength="100" />
        </el-form-item>
      </el-col>
      <el-col :xs="24" :sm="12" :lg="6">
        <el-form-item label="组件类型" required>
          <el-select v-model="field.type" class="!w-100%" @change="normalizeType">
            <el-option
              v-for="item in availableFieldTypes"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :xs="12" :sm="6" :lg="3">
        <el-form-item label="必填"><el-switch v-model="field.required" /></el-form-item>
      </el-col>
      <el-col :xs="12" :sm="6" :lg="3">
        <el-form-item label="可检索"><el-switch v-model="field.searchable" /></el-form-item>
      </el-col>
    </el-row>

    <el-row :gutter="12">
      <el-col v-if="isDictionary" :xs="24" :sm="12" :lg="8">
        <el-form-item label="系统字典" required>
          <el-select v-model="field.dictType" filterable class="!w-100%" placeholder="选择管理员维护的字典">
            <el-option
              v-for="dict in dictTypes"
              :key="dict.type"
              :label="`${dict.name}（${dict.type}）`"
              :value="dict.type"
            />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col v-if="isDictionary && !nested" :xs="24" :sm="12" :lg="8">
        <el-form-item label="推荐维度">
          <el-select
            v-model="field.recommendationDimension"
            clearable
            class="!w-100%"
            placeholder="不参与推荐"
            @change="applyRecommendationDictionary"
          >
            <el-option label="账号类型" value="account_type" />
            <el-option label="专业方向" value="profession" />
            <el-option label="账号时期" value="account_stage" />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col v-if="field.recommendationDimension" :xs="12" :sm="8" :lg="4">
        <el-form-item label="允许不限"><el-switch v-model="field.allowUnlimited" /></el-form-item>
      </el-col>
      <el-col v-if="supportsMultiple" :xs="12" :sm="8" :lg="4">
        <el-form-item label="允许多选">
          <el-switch v-model="field.multiple" @change="normalizeMultiple" />
        </el-form-item>
      </el-col>
      <el-col v-if="supportsLength" :xs="24" :sm="8" :lg="4">
        <el-form-item label="最大长度">
          <el-input-number v-model="field.maxLength" :min="1" :max="200000" controls-position="right" />
        </el-form-item>
      </el-col>
      <el-col v-if="field.type === 'number'" :xs="12" :sm="8" :lg="4">
        <el-form-item label="最小值"><el-input-number v-model="field.min" controls-position="right" /></el-form-item>
      </el-col>
      <el-col v-if="field.type === 'number'" :xs="12" :sm="8" :lg="4">
        <el-form-item label="最大值"><el-input-number v-model="field.max" controls-position="right" /></el-form-item>
      </el-col>
      <el-col v-if="supportsCount" :xs="12" :sm="8" :lg="4">
        <el-form-item label="最少数量">
          <el-input-number v-model="field.minCount" :min="0" :max="field.type === 'repeat-group' ? 100 : 500" controls-position="right" />
        </el-form-item>
      </el-col>
      <el-col v-if="supportsCount" :xs="12" :sm="8" :lg="4">
        <el-form-item label="最多数量">
          <el-input-number v-model="field.maxCount" :min="1" :max="field.type === 'repeat-group' ? 100 : fileType ? 100 : 500" controls-position="right" />
        </el-form-item>
      </el-col>
      <el-col v-if="fileType" :xs="12" :sm="8" :lg="4">
        <el-form-item label="单文件 MB">
          <el-input-number v-model="field.maxSizeMb" :min="1" :max="1024" controls-position="right" />
        </el-form-item>
      </el-col>
      <el-col v-if="fileType" :xs="24" :sm="16" :lg="8">
        <el-form-item label="允许扩展名">
          <el-select
            v-model="field.allowedExtensions"
            multiple
            filterable
            allow-create
            default-first-option
            class="!w-100%"
            placeholder="留空表示不限制"
          />
        </el-form-item>
      </el-col>
    </el-row>

    <div v-if="field.type === 'repeat-group'" class="schema-field-card__children">
      <div class="schema-field-card__children-title">
        <span>重复组子字段</span>
        <el-button type="primary" plain size="small" @click="addChild">
          <Icon icon="ep:plus" class="mr-4px" />新增子字段
        </el-button>
      </div>
      <el-empty v-if="!field.children?.length" description="重复组至少需要一个子字段" :image-size="56" />
      <MaterialSchemaFieldCard
        v-for="(child, childIndex) in field.children || []"
        :key="child.key || childIndex"
        :field="child"
        :index="childIndex"
        :total="field.children?.length || 0"
        :dict-types="dictTypes"
        nested
        @move="moveChild(childIndex, $event)"
        @remove="removeChild(childIndex)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import type {
  MaterialFieldDefinition,
  MaterialFieldType,
  RecommendationDimension
} from '@/api/zsjos/material'

defineOptions({ name: 'MaterialSchemaFieldCard' })

const props = defineProps<{
  field: MaterialFieldDefinition
  index: number
  total: number
  dictTypes: Array<{ name: string; type: string }>
  nested?: boolean
}>()

defineEmits<{
  move: [offset: number]
  remove: []
}>()

const fieldTypes: Array<{ value: MaterialFieldType; label: string }> = [
  { value: 'text', label: '单行文本' },
  { value: 'textarea', label: '多行文本' },
  { value: 'rich-text', label: '富文本' },
  { value: 'number', label: '数字' },
  { value: 'date', label: '日期' },
  { value: 'datetime', label: '日期时间' },
  { value: 'dict-single', label: '字典单选' },
  { value: 'dict-multi', label: '字典多选' },
  { value: 'employee', label: '员工' },
  { value: 'department', label: '部门' },
  { value: 'image', label: '图片' },
  { value: 'video', label: '视频' },
  { value: 'attachment', label: '附件' },
  { value: 'https-link', label: 'HTTPS 外链' },
  { value: 'repeat-group', label: '重复字段组' }
]

const availableFieldTypes = computed(() =>
  props.nested ? fieldTypes.filter((item) => item.value !== 'repeat-group') : fieldTypes
)
const isDictionary = computed(() => ['dict-single', 'dict-multi'].includes(props.field.type))
const supportsLength = computed(() => ['text', 'textarea', 'rich-text'].includes(props.field.type))
const supportsMultiple = computed(() => ['employee', 'department'].includes(props.field.type))
const fileType = computed(() => ['image', 'video', 'attachment'].includes(props.field.type))
const supportsCount = computed(
  () =>
    props.field.type === 'dict-multi' ||
    props.field.type === 'repeat-group' ||
    fileType.value ||
    (supportsMultiple.value && props.field.multiple)
)

const recommendationDict: Record<RecommendationDimension, string> = {
  account_type: 'zsjos_material_account_type',
  profession: 'zsjos_material_profession',
  account_stage: 'zsjos_media_account_stage'
}

const applyRecommendationDictionary = (dimension?: RecommendationDimension) => {
  if (dimension) props.field.dictType = recommendationDict[dimension]
  else props.field.allowUnlimited = false
}

const normalizeMultiple = () => {
  if (!props.field.multiple) {
    props.field.minCount = undefined
    props.field.maxCount = undefined
  }
}

const normalizeType = () => {
  const field = props.field
  if (!['dict-single', 'dict-multi'].includes(field.type)) {
    field.dictType = undefined
    field.recommendationDimension = undefined
    field.allowUnlimited = false
  }
  if (!['employee', 'department'].includes(field.type)) field.multiple = undefined
  if (!['text', 'textarea', 'rich-text'].includes(field.type)) field.maxLength = undefined
  if (field.type !== 'number') {
    field.min = undefined
    field.max = undefined
  }
  if (!['dict-multi', 'employee', 'department', 'image', 'video', 'attachment', 'repeat-group'].includes(field.type)) {
    field.minCount = undefined
    field.maxCount = undefined
  }
  if (['employee', 'department'].includes(field.type) && !field.multiple) normalizeMultiple()
  if (!['image', 'video', 'attachment'].includes(field.type)) {
    field.maxSizeMb = undefined
    field.allowedExtensions = undefined
  }
  if (field.type === 'repeat-group') {
    field.children ||= [newField(false)]
    field.searchable = false
  } else {
    field.children = undefined
  }
}

const newField = (allowRepeat = false): MaterialFieldDefinition => ({
  key: `field_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 6)}`,
  label: '新字段',
  type: allowRepeat ? 'repeat-group' : 'text',
  required: false,
  searchable: false
})

const addChild = () => {
  props.field.children ||= []
  props.field.children.push(newField())
}
const moveChild = (index: number, offset: number) => {
  const children = props.field.children || []
  const target = index + offset
  if (target < 0 || target >= children.length) return
  ;[children[index], children[target]] = [children[target], children[index]]
}
const removeChild = (index: number) => props.field.children?.splice(index, 1)
</script>

<style scoped>
.schema-field-card {
  padding: 16px;
  margin-bottom: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  background: var(--el-bg-color);
}

.schema-field-card__header,
.schema-field-card__children-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.schema-field-card__children {
  padding: 12px;
  border-left: 3px solid var(--el-color-primary-light-5);
  background: var(--el-fill-color-lighter);
}

.schema-field-card__children .schema-field-card {
  margin-bottom: 8px;
}
</style>
