<template>
  <Dialog v-model="dialogVisible" title="导入分类配置" width="920px">
    <el-steps :active="result ? 1 : 0" finish-status="success" simple class="mb-4">
      <el-step title="选择配置文件" />
      <el-step title="预检差异" />
      <el-step title="确认导入" />
    </el-steps>

    <el-upload
      ref="uploadRef"
      v-model:file-list="fileList"
      :auto-upload="false"
      :limit="1"
      :on-change="handleFileChange"
      :on-exceed="handleExceed"
      :disabled="loading"
      accept=".xlsx"
      drag
    >
      <Icon icon="ep:upload-filled" />
      <div class="el-upload__text">拖入分类配置，或<em>选择文件</em></div>
      <template #tip>
        <div class="flex items-center justify-between text-xs">
          <span>模板包含“分类”和“字段”两个工作表</span>
          <el-button link type="primary" @click.stop="downloadTemplate">下载配置模板</el-button>
        </div>
      </template>
    </el-upload>

    <template v-if="result">
      <div class="my-4 grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-8">
        <el-statistic title="分类总数" :value="result.categoryCount" />
        <el-statistic title="子分类" :value="result.leafCategoryCount" />
        <el-statistic title="字段总数" :value="result.fieldCount" />
        <el-statistic title="新增" :value="result.createCount" />
        <el-statistic title="更新" :value="result.updateCount" />
        <el-statistic title="跳过" :value="result.skipCount" />
        <el-statistic title="冲突" :value="result.conflictCount" />
      </div>
      <div class="mb-3 grid gap-2 sm:grid-cols-3">
        <el-alert
          :title="result.leafCategoryCount > 0 ? `子分类已识别 ${result.leafCategoryCount} 个` : '未识别到子分类'"
          :type="result.leafCategoryCount > 0 ? 'success' : 'warning'"
          :closable="false"
        />
        <el-alert
          :title="result.legacyFieldCount === 0 ? '未发现旧原表字段' : `发现旧原表字段 ${result.legacyFieldCount} 个`"
          :type="result.legacyFieldCount === 0 ? 'success' : 'error'"
          :closable="false"
        />
        <el-alert
          :title="result.credentialFieldCount === 0 ? '未发现密码/凭据字段' : `发现凭据字段 ${result.credentialFieldCount} 个`"
          :type="result.credentialFieldCount === 0 ? 'success' : 'error'"
          :closable="false"
        />
      </div>
      <el-alert
        class="mb-3"
        :title="result.allManagementFieldsOptional ? '管理端字段全部为选填' : '存在管理端必填字段，请检查模板'"
        :type="result.allManagementFieldsOptional ? 'success' : 'error'"
        :closable="false"
      />
      <el-alert
        v-if="result.conflictCount > 0"
        title="存在冲突，请修正模板后重新预检"
        type="error"
        :closable="false"
        show-icon
        class="mb-3"
      />
      <el-table :data="result.items" max-height="400" border>
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">{{ row.kind === 'CATEGORY' ? '分类' : '字段' }}</template>
        </el-table-column>
        <el-table-column label="编码/标识" prop="code" min-width="150" show-overflow-tooltip />
        <el-table-column label="名称" prop="name" min-width="180" show-overflow-tooltip />
        <el-table-column label="处理" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="actionType(row.action)">{{ actionText(row.action) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="说明" prop="message" min-width="220" show-overflow-tooltip />
      </el-table>
    </template>

    <template #footer>
      <el-button :loading="loading" :disabled="!selectedFile" @click="preview">预 检</el-button>
      <el-button
        type="primary"
        :loading="loading"
        :disabled="!result || result.conflictCount > 0"
        @click="commit"
      >
        确认导入
      </el-button>
      <el-button @click="dialogVisible = false">关 闭</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import type { UploadFile, UploadUserFile } from 'element-plus'
import download from '@/utils/download'
import * as CategoryApi from '@/api/eam/category'

defineOptions({ name: 'EamCategoryImportForm' })

const emit = defineEmits(['success'])
const message = useMessage()
const dialogVisible = ref(false)
const loading = ref(false)
const uploadRef = ref()
const fileList = ref<UploadUserFile[]>([])
const selectedFile = ref<File>()
const result = ref<CategoryApi.CategoryImportRespVO>()

const open = () => {
  dialogVisible.value = true
  selectedFile.value = undefined
  result.value = undefined
  fileList.value = []
  uploadRef.value?.clearFiles()
}
defineExpose({ open })

const handleFileChange = (file: UploadFile) => {
  selectedFile.value = file.raw
  result.value = undefined
}

const handleExceed = () => message.warning('每次只能选择一个配置文件')

const preview = async () => {
  if (!selectedFile.value) return
  loading.value = true
  try {
    result.value = await CategoryApi.previewImport(selectedFile.value)
  } finally {
    loading.value = false
  }
}

const commit = async () => {
  if (!selectedFile.value || !result.value || result.value.conflictCount > 0) return
  loading.value = true
  try {
    result.value = await CategoryApi.commitImport(selectedFile.value)
    message.success(
      `分类配置已导入：新增 ${result.value.createCount}，更新 ${result.value.updateCount}，跳过 ${result.value.skipCount}`
    )
    emit('success')
  } finally {
    loading.value = false
  }
}

const downloadTemplate = async () => {
  download.excel(await CategoryApi.importTemplate(), '中世健EAM分类配置模板.xlsx')
}

const actionText = (action: CategoryApi.CategoryImportItemVO['action']) =>
  ({ CREATE: '新增', UPDATE: '更新', SKIP: '跳过', CONFLICT: '冲突' })[action]

const actionType = (action: CategoryApi.CategoryImportItemVO['action']) =>
  ({ CREATE: 'success', UPDATE: 'warning', SKIP: 'info', CONFLICT: 'danger' })[action] as any
</script>
