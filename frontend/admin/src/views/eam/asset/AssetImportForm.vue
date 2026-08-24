<template>
  <Dialog v-model="dialogVisible" title="导入资产台账" width="1040px">
    <el-steps :active="step" finish-status="success" simple class="mb-4">
      <el-step title="上传台账" />
      <el-step title="预检结果" />
      <el-step title="确认导入" />
    </el-steps>

    <el-upload
      ref="uploadRef"
      v-model:file-list="fileList"
      :auto-upload="false"
      :limit="1"
      :on-change="handleFileChange"
      :on-exceed="handleExceed"
      :disabled="loading || committed"
      accept=".xlsx"
      drag
    >
      <Icon icon="ep:upload-filled" />
      <div class="el-upload__text">拖入中世健资产台账，或<em>选择文件</em></div>
      <template #tip>
        <div class="flex flex-wrap items-center justify-between gap-2 text-xs">
          <span>仅读取“在岗资产初始申报表”，从第 3 行开始预检</span>
          <el-button link type="primary" @click.stop="downloadTemplate">下载 54 列模板</el-button>
        </div>
      </template>
    </el-upload>

    <div class="mt-3 flex items-center gap-2">
      <el-checkbox v-model="updateExisting" :disabled="loading || committed" @change="clearPreview">
        更新已有资产标签
      </el-checkbox>
      <el-text size="small" type="info">默认跳过已有资产；勾选后预检会显示待更新行</el-text>
    </div>

    <template v-if="result">
      <div class="my-4 grid grid-cols-2 gap-3 sm:grid-cols-5">
        <el-statistic title="有效行" :value="result.totalRows" />
        <el-statistic title="新增" :value="result.createCount" />
        <el-statistic title="更新" :value="result.updateCount" />
        <el-statistic title="跳过" :value="result.skipCount" />
        <el-statistic title="警告" :value="result.warningCount" />
      </div>
      <el-alert
        v-if="result.warningCount > 0"
        title="警告不会阻止导入；请展开对应行核对默认值和人员匹配结果"
        type="warning"
        :closable="false"
        show-icon
        class="mb-3"
      />
      <el-alert
        v-if="committed"
        :title="`导入完成，批次号 ${result.batchId}`"
        type="success"
        :closable="false"
        show-icon
        class="mb-3"
      />
      <el-table :data="result.rows" max-height="460" border row-key="rowNum">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="px-4 py-2">
              <el-descriptions :column="2" border size="small">
                <el-descriptions-item
                  v-for="(value, key) in row.mappedFields"
                  :key="key"
                  :label="String(key)"
                >
                  {{ value }}
                </el-descriptions-item>
              </el-descriptions>
              <div v-if="row.defaultedFields.length" class="mt-3">
                <div class="mb-1 text-sm font-medium">自动默认值</div>
                <el-tag
                  v-for="item in row.defaultedFields"
                  :key="item"
                  type="info"
                  class="mb-1 mr-2"
                >
                  {{ item }}
                </el-tag>
              </div>
              <div v-if="row.warnings.length" class="mt-2">
                <div class="mb-1 text-sm font-medium">警告</div>
                <el-tag v-for="item in row.warnings" :key="item" type="warning" class="mb-1 mr-2">
                  {{ item }}
                </el-tag>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Excel 行" prop="rowNum" width="85" align="center" />
        <el-table-column label="资产标签" prop="assetCode" min-width="130">
          <template #default="{ row }">{{ row.assetCode || '自动生成' }}</template>
        </el-table-column>
        <el-table-column label="资产名称" prop="name" min-width="150" show-overflow-tooltip />
        <el-table-column label="分类" prop="categoryName" min-width="200" show-overflow-tooltip />
        <el-table-column label="数量" width="80" align="center">
          <template #default="{ row }">{{ row.quantity }}</template>
        </el-table-column>
        <el-table-column label="人员匹配" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.matchedUserName || row.useUserName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="处理" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="actionType(row.action)">{{ actionText(row.action) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="警告" width="80" align="center">
          <template #default="{ row }">
            <el-badge :value="row.warnings.length" :hidden="row.warnings.length === 0" />
          </template>
        </el-table-column>
      </el-table>
    </template>

    <template #footer>
      <el-button :loading="loading" :disabled="!selectedFile || committed" @click="preview">
        预 检
      </el-button>
      <el-button type="primary" :loading="loading" :disabled="!result || committed" @click="commit">
        确认导入
      </el-button>
      <el-button @click="dialogVisible = false">关 闭</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import type { UploadFile, UploadUserFile } from 'element-plus'
import download from '@/utils/download'
import * as AssetApi from '@/api/eam/asset'

defineOptions({ name: 'EamAssetImportForm' })

const emit = defineEmits(['success'])
const message = useMessage()
const dialogVisible = ref(false)
const loading = ref(false)
const committed = ref(false)
const uploadRef = ref()
const fileList = ref<UploadUserFile[]>([])
const selectedFile = ref<File>()
const updateExisting = ref(false)
const result = ref<AssetApi.AssetImportPreviewRespVO>()
const step = computed(() => (committed.value ? 3 : result.value ? 2 : selectedFile.value ? 1 : 0))

const open = () => {
  dialogVisible.value = true
  loading.value = false
  committed.value = false
  selectedFile.value = undefined
  updateExisting.value = false
  result.value = undefined
  fileList.value = []
  uploadRef.value?.clearFiles()
}
defineExpose({ open })

const handleFileChange = (file: UploadFile) => {
  selectedFile.value = file.raw
  committed.value = false
  result.value = undefined
}

const handleExceed = () => message.warning('每次只能选择一个资产台账文件')
const clearPreview = () => {
  result.value = undefined
}

const preview = async () => {
  if (!selectedFile.value) return
  loading.value = true
  try {
    result.value = await AssetApi.previewLedgerImport(selectedFile.value, updateExisting.value)
  } finally {
    loading.value = false
  }
}

const commit = async () => {
  if (!selectedFile.value || !result.value) return
  loading.value = true
  try {
    result.value = await AssetApi.commitLedgerImport(selectedFile.value, updateExisting.value)
    committed.value = true
    message.success(
      `台账导入完成：新增 ${result.value.createCount}，更新 ${result.value.updateCount}，跳过 ${result.value.skipCount}`
    )
    emit('success')
  } finally {
    loading.value = false
  }
}

const downloadTemplate = async () => {
  download.excel(await AssetApi.importTemplate(), '中世健资产台账导入模板.xlsx')
}

const actionText = (action: AssetApi.AssetImportRowVO['action']) =>
  ({ CREATE: '新增', UPDATE: '更新', SKIP_EXISTING: '已有跳过', SKIP_SAME_FILE: '重复跳过' })[
    action
  ]

const actionType = (action: AssetApi.AssetImportRowVO['action']) =>
  ({ CREATE: 'success', UPDATE: 'warning', SKIP_EXISTING: 'info', SKIP_SAME_FILE: 'info' })[
    action
  ] as any
</script>
