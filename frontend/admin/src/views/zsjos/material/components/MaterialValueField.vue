<template>
  <div v-if="field.type === 'repeat-group'" class="material-repeat-group">
    <div class="material-repeat-group__header">
      <div>
        <strong>{{ field.label }}</strong>
        <el-tag v-if="field.required" size="small" type="danger">必填</el-tag>
      </div>
      <el-button
        v-if="!readonly"
        type="primary"
        plain
        size="small"
        :disabled="rows.length >= (field.maxCount || 100)"
        @click="addRow"
      >
        <Icon icon="ep:plus" class="mr-4px" />新增一组
      </el-button>
    </div>
    <el-empty v-if="!rows.length" :image-size="48" description="暂无内容" />
    <div v-for="(row, rowIndex) in rows" :key="rowIndex" class="material-repeat-group__row">
      <div class="material-repeat-group__row-header">
        <span>第 {{ rowIndex + 1 }} 组</span>
        <el-button v-if="!readonly" link type="danger" @click="removeRow(rowIndex)">删除</el-button>
      </div>
      <el-row :gutter="16">
        <el-col
          v-for="child in field.children || []"
          :key="child.key"
          :xs="24"
          :lg="child.type === 'rich-text' || child.type === 'textarea' ? 24 : 12"
        >
          <MaterialValueField
            :field="child"
            :model-value="row[child.key]"
            :snapshot="snapshotRows[rowIndex]?.[child.key]"
            :dict-data="dictData"
            :users="users"
            :departments="departments"
            :files="files"
            :field-path="`${fieldPath}.${child.key}`"
            :group-index="rowIndex"
            :readonly="readonly"
            @update:model-value="updateChild(rowIndex, child.key, $event)"
          />
        </el-col>
      </el-row>
    </div>
  </div>

  <el-form-item v-else :label="field.label" :required="field.required" class="material-value-field">
    <ResourceLinkInput
      v-if="field.type === 'https-link'"
      :model-value="stringValue"
      :maxlength="field.maxLength"
      :disabled="readonly"
      placeholder="https://"
      @update:model-value="update"
    />
    <el-input
      v-else-if="field.type === 'text'"
      :model-value="stringValue"
      :maxlength="field.maxLength"
      :disabled="readonly"
      clearable
      placeholder=""
      @update:model-value="update"
    />
    <el-input
      v-else-if="field.type === 'textarea'"
      :model-value="stringValue"
      type="textarea"
      :rows="4"
      :maxlength="field.maxLength"
      :disabled="readonly"
      show-word-limit
      @update:model-value="update"
    />
    <Editor
      v-else-if="field.type === 'rich-text'"
      :model-value="stringValue"
      :readonly="readonly"
      height="240px"
      directory="zsjos-material-rich-text"
      @update:model-value="update"
    />
    <el-input-number
      v-else-if="field.type === 'number'"
      :model-value="numberValue"
      :min="field.min"
      :max="field.max"
      :disabled="readonly"
      controls-position="right"
      class="!w-100%"
      @update:model-value="update"
    />
    <el-date-picker
      v-else-if="field.type === 'date'"
      :model-value="stringValue || undefined"
      type="date"
      value-format="YYYY-MM-DD"
      :disabled="readonly"
      class="!w-100%"
      @update:model-value="update"
    />
    <el-date-picker
      v-else-if="field.type === 'datetime'"
      :model-value="stringValue || undefined"
      type="datetime"
      value-format="YYYY-MM-DDTHH:mm:ss"
      :disabled="readonly"
      class="!w-100%"
      @update:model-value="update"
    />
    <span
      v-else-if="readonly && snapshotBackedField"
      class="material-value-field__snapshot"
    >
      {{ snapshotDisplay }}
    </span>
    <el-select
      v-else-if="field.type === 'dict-single' || field.type === 'dict-multi'"
      :model-value="dictionaryValue"
      :multiple="field.type === 'dict-multi'"
      :multiple-limit="field.maxCount || 0"
      :disabled="readonly"
      filterable
      clearable
      class="!w-100%"
      placeholder="请选择"
      @update:model-value="update"
    >
      <el-option v-if="field.allowUnlimited" label="不限" value="__ALL__" />
      <el-option
        v-for="option in dictionaryOptions"
        :key="String(option.value)"
        :label="option.label"
        :value="String(option.value)"
      />
    </el-select>
    <el-select
      v-else-if="field.type === 'employee'"
      :model-value="entityValue"
      :multiple="field.multiple"
      :multiple-limit="field.maxCount || 0"
      :disabled="readonly"
      filterable
      clearable
      class="!w-100%"
      placeholder="请选择员工"
      @update:model-value="update"
    >
      <el-option v-for="user in users" :key="user.id" :label="user.nickname" :value="user.id" />
    </el-select>
    <el-select
      v-else-if="field.type === 'department'"
      :model-value="entityValue"
      :multiple="field.multiple"
      :multiple-limit="field.maxCount || 0"
      :disabled="readonly"
      filterable
      clearable
      class="!w-100%"
      placeholder="请选择部门"
      @update:model-value="update"
    >
      <el-option v-for="dept in departments" :key="dept.id" :label="dept.name" :value="dept.id" />
    </el-select>
    <div v-else-if="fileType" class="material-file-field">
      <div v-if="selectedFiles.length" class="material-file-field__list">
        <div v-for="file in selectedFiles" :key="file.fileId" class="material-file-field__item">
          <el-image
            v-if="field.type === 'image' && file.previewUrl"
            :src="file.previewUrl"
            :preview-src-list="[file.previewUrl]"
            fit="cover"
          />
          <video v-else-if="field.type === 'video' && file.previewUrl" :src="file.previewUrl" controls></video>
          <el-link v-else-if="file.previewUrl" :href="file.previewUrl" target="_blank" type="primary">
            {{ file.name }}
          </el-link>
          <span v-else>{{ file.name || `文件 ${file.fileId}` }}</span>
          <el-button v-if="!readonly" link type="danger" @click="removeFile(file.fileId)">
            <Icon icon="ep:delete" />
          </el-button>
        </div>
      </div>
      <ClipboardUploadActions :disabled="readonly || uploading || selectedFiles.length >= (field.maxCount || 20)" :can-paste="!readonly && !uploading && selectedFiles.length < (field.maxCount || 20)" @files="handlePasteFiles"><el-upload
        v-if="!readonly"
        :show-file-list="false"
        :http-request="upload"
        :accept="accept"
        :disabled="uploading || selectedFiles.length >= (field.maxCount || 20)"
      >
        <el-button :loading="uploading" type="primary" plain>
          <Icon icon="ep:upload" class="mr-4px" />上传附件
        </el-button>
      </el-upload></ClipboardUploadActions>
    </div>
  </el-form-item>
</template>

<script setup lang="ts">
import ResourceLinkInput from '@/components/ResourceLinkInput/index.vue'
import type { UploadRequestOptions } from 'element-plus'
import { Editor } from '@/components/Editor'
import { uploadMaterialFile, type MaterialFieldDefinition, type MaterialFile } from '@/api/zsjos/material'
import ClipboardUploadActions from '@/components/UploadFile/src/ClipboardUploadActions.vue'

defineOptions({ name: 'MaterialValueField' })

const props = withDefaults(
  defineProps<{
    field: MaterialFieldDefinition
    modelValue?: unknown
    snapshot?: unknown
    dictData: Array<{ dictType: string; label: string; value: string | number }>
    users: Array<{ id: number; nickname: string }>
    departments: Array<{ id: number; name: string }>
    files: MaterialFile[]
    fieldPath?: string
    groupIndex?: number
    readonly?: boolean
  }>(),
  { fieldPath: '', groupIndex: -1, readonly: false }
)
const emit = defineEmits<{ 'update:modelValue': [value: unknown] }>()
const message = useMessage()
const uploading = ref(false)
const uploadedFiles = ref<MaterialFile[]>([])
const handlePasteFiles = (files: File[]) => { const file = files[0]; if (file) void upload({ file } as UploadRequestOptions) }

const fieldPath = computed(() => props.fieldPath || props.field.key)
const rows = computed<Record<string, unknown>[]>(() =>
  Array.isArray(props.modelValue) ? (props.modelValue as Record<string, unknown>[]) : []
)
const snapshotRows = computed<Record<string, unknown>[]>(() =>
  Array.isArray(props.snapshot) ? (props.snapshot as Record<string, unknown>[]) : []
)
const stringValue = computed(() => (props.modelValue == null ? '' : String(props.modelValue)))
const numberValue = computed(() =>
  typeof props.modelValue === 'number' ? props.modelValue : props.modelValue == null ? undefined : Number(props.modelValue)
)
const dictionaryValue = computed<string | string[] | undefined>(() => {
  if (Array.isArray(props.modelValue)) return props.modelValue.map(String)
  return props.modelValue == null ? undefined : String(props.modelValue)
})
const entityValue = computed<number | number[] | undefined>(() => {
  if (Array.isArray(props.modelValue)) return props.modelValue.map(Number).filter(Number.isFinite)
  return props.modelValue == null ? undefined : Number(props.modelValue)
})
const fileType = computed(() => ['image', 'video', 'attachment'].includes(props.field.type))
const snapshotBackedField = computed(() =>
  ['dict-single', 'dict-multi', 'employee', 'department'].includes(props.field.type)
)
const displaySnapshot = (value: unknown): string => {
  if (value == null || value === '') return '未填写'
  if (Array.isArray(value)) {
    const labels = value.map(displaySnapshot).filter((label) => label !== '未填写')
    return labels.length ? labels.join('、') : '未填写'
  }
  if (typeof value === 'object') {
    const object = value as Record<string, unknown>
    if (object.label != null) return String(object.label)
    const labels = Object.values(object).map(displaySnapshot).filter((label) => label !== '未填写')
    return labels.length ? labels.join('、') : '未填写'
  }
  return String(value)
}
const snapshotDisplay = computed(() =>
  displaySnapshot(props.snapshot == null ? props.modelValue : props.snapshot)
)
const fileIds = computed<number[]>(() =>
  Array.isArray(props.modelValue)
    ? props.modelValue.map(Number).filter(Number.isFinite)
    : props.modelValue == null
      ? []
      : [Number(props.modelValue)].filter(Number.isFinite)
)
const selectedFiles = computed(() => {
  const known = [...props.files, ...uploadedFiles.value]
  return fileIds.value.map(
    (fileId) =>
      known.find(
        (file) =>
          file.fileId === fileId &&
          file.fieldKey === fieldPath.value &&
          file.groupIndex === props.groupIndex
      ) || {
        id: -fileId,
        fileId,
        fieldKey: fieldPath.value,
        groupIndex: props.groupIndex,
        name: `文件 ${fileId}`,
        contentType: '',
        size: 0
      }
  )
})
const dictionaryOptions = computed(() =>
  props.dictData.filter((option) => option.dictType === props.field.dictType)
)
const accept = computed(() => {
  if (props.field.allowedExtensions?.length) {
    return props.field.allowedExtensions.map((extension) => `.${extension.replace(/^\./, '')}`).join(',')
  }
  if (props.field.type === 'image') return 'image/*'
  if (props.field.type === 'video') return 'video/*'
  return undefined
})

const update = (value: unknown) => emit('update:modelValue', value)
const addRow = () => emit('update:modelValue', [...rows.value, {}])
const removeRow = (index: number) =>
  emit('update:modelValue', rows.value.filter((_, current) => current !== index))
const updateChild = (rowIndex: number, key: string, value: unknown) => {
  const next = rows.value.map((row, current) =>
    current === rowIndex ? { ...row, [key]: value } : row
  )
  emit('update:modelValue', next)
}
const removeFile = (fileId: number) =>
  emit('update:modelValue', fileIds.value.filter((current) => current !== fileId))
const upload = async (options: UploadRequestOptions) => {
  uploading.value = true
  try {
    const result = await uploadMaterialFile(options.file)
    uploadedFiles.value.push({
      id: -result.fileId,
      fileId: result.fileId,
      fieldKey: fieldPath.value,
      groupIndex: props.groupIndex,
      name: result.name,
      contentType: result.contentType,
      size: result.size,
      previewUrl: result.previewUrl
    })
    emit('update:modelValue', [...fileIds.value, result.fileId])
    options.onSuccess?.(result)
  } catch (error) {
    message.error('素材文件上传失败')
    throw error
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.material-value-field {
  margin-bottom: 18px;
}

.material-value-field__snapshot {
  display: block;
  min-height: 32px;
  padding: 5px 0;
  overflow-wrap: anywhere;
  color: var(--el-text-color-primary);
}

.material-repeat-group {
  padding: 14px;
  margin-bottom: 18px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
}

.material-repeat-group__header,
.material-repeat-group__row-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.material-repeat-group__header {
  margin-bottom: 12px;
}

.material-repeat-group__header > div {
  display: flex;
  align-items: center;
  gap: 8px;
}

.material-repeat-group__row {
  padding: 12px 12px 0;
  margin-top: 10px;
  background: var(--el-fill-color-lighter);
  border-radius: 4px;
}

.material-repeat-group__row-header {
  margin-bottom: 8px;
  color: var(--el-text-color-secondary);
}

.material-file-field,
.material-file-field__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.material-file-field__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 40px;
  padding: 8px 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
}

.material-file-field__item .el-image,
.material-file-field__item video {
  width: 96px;
  height: 64px;
  object-fit: cover;
  border-radius: 4px;
}
</style>
