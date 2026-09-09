<template>
  <ContentWrap>
    <el-alert
      v-if="typeError"
      :title="typeError"
      type="error"
      show-icon
      :closable="false"
      class="mb-12px"
    >
      <template #default>
        <el-button link type="primary" :loading="typeLoading" @click="loadTypes">
          重试加载素材类型
        </el-button>
      </template>
    </el-alert>
    <div class="material-import-toolbar">
      <el-form :inline="true" :model="query">
        <el-form-item label="素材类型">
          <el-select
            v-model="query.materialTypeId"
            clearable
            filterable
            :disabled="!typesReady"
            class="!w-190px"
          >
            <el-option v-for="type in importTypes" :key="type.id" :label="type.name" :value="type.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable class="!w-150px">
            <el-option label="已预检" value="PREVIEWED" />
            <el-option label="已确认" value="COMMITTED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
      <el-space>
        <el-button
          v-hasPermi="['zsjos:material-import:download']"
          :loading="downloading"
          :disabled="!typesReady"
          @click="downloadTemplate"
        >
          <Icon icon="ep:download" class="mr-4px" />下载模板
        </el-button>
        <el-button
          v-hasPermi="['zsjos:material-import:preview']"
          type="primary"
          :disabled="!typesReady"
          @click="openImport"
        >
          <Icon icon="ep:upload" class="mr-4px" />上传预检
        </el-button>
      </el-space>
    </div>
  </ContentWrap>

  <ContentWrap v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="mb-12px">
      <template #default><el-button link type="primary" @click="load">重试</el-button></template>
    </el-alert>
    <el-table :data="rows" empty-text="暂无导入记录">
      <el-table-column prop="batchNo" label="导入批次" min-width="190" />
      <el-table-column prop="materialTypeName" label="素材类型" width="150" />
      <el-table-column prop="sourceFileName" label="源文件" min-width="210" show-overflow-tooltip />
      <el-table-column label="预检结果" width="220">
        <template #default="{ row }">共 {{ row.totalCount }} 行 · 可导入 {{ row.successCount }} 行 · 错误 {{ row.failureCount }} 行</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }"><el-tag :type="row.status === 'COMMITTED' ? 'success' : 'warning'">{{ statusLabel(row.status) }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="createTime" label="预检时间" min-width="170"><template #default="{ row }">{{ formatTime(row.createTime) }}</template></el-table-column>
      <el-table-column prop="confirmedAt" label="确认时间" min-width="170"><template #default="{ row }">{{ formatTime(row.confirmedAt) }}</template></el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
          <el-button
            v-if="row.status === 'PREVIEWED' && row.successCount > 0"
            v-hasPermi="['zsjos:material-import:commit']"
            link
            type="primary"
            @click="commit(row)"
          >确认导入</el-button>
          <el-button
            v-if="row.failureCount > 0"
            v-hasPermi="['zsjos:material-import:error-download']"
            link
            :loading="reportDownloadingId === row.id"
            @click="downloadErrors(row)"
          >错误报告</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination v-model:page="query.pageNo" v-model:limit="query.pageSize" :total="total" @pagination="load" />
  </ContentWrap>

  <Dialog v-model="importVisible" title="预检素材导入" width="620px">
    <el-form label-width="100px">
      <el-form-item label="素材类型" required>
        <el-select
          v-model="importTypeId"
          filterable
          :disabled="!typesReady"
          class="!w-100%"
          @change="resetSelectedFile"
        >
          <el-option v-for="type in importTypes" :key="type.id" :label="type.name" :value="type.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="Excel 文件" required>
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :limit="1"
          :disabled="!typesReady"
          accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
          :on-change="selectFile"
          :on-remove="handleRemove"
        >
          <el-button><Icon icon="ep:document" class="mr-4px" />选择 .xlsx 文件</el-button>
        </el-upload>
      </el-form-item>
    </el-form>
    <el-alert
      v-if="previewResult"
      :title="`预检完成：共 ${previewResult.totalCount} 行，可导入 ${previewResult.successCount} 行，错误 ${previewResult.failureCount} 行`"
      :type="previewResult.failureCount ? 'warning' : 'success'"
      show-icon
      :closable="false"
      class="mb-12px"
    />
    <el-table v-if="previewResult?.errors?.length" :data="previewResult.errors" max-height="280">
      <el-table-column prop="sheetName" label="工作表" width="120" />
      <el-table-column prop="rowNo" label="行" width="70" />
      <el-table-column prop="fieldKey" label="字段" width="130" />
      <el-table-column prop="errorMessage" label="问题" min-width="220" />
    </el-table>
    <template #footer>
      <el-button @click="importVisible = false">关闭</el-button>
      <el-button
        type="primary"
        :loading="previewing"
        :disabled="!typesReady || !importTypeId || !selectedFile"
        @click="preview"
      >
        开始预检
      </el-button>
      <el-button
        v-if="previewResult?.status === 'PREVIEWED' && previewResult.successCount > 0"
        v-hasPermi="['zsjos:material-import:commit']"
        type="success"
        :loading="committing"
        @click="commit(previewResult)"
      >确认导入合法行</el-button>
    </template>
  </Dialog>

  <el-drawer v-model="detailVisible" size="65%" title="导入批次详情">
    <div v-loading="detailLoading">
      <el-alert v-if="detailError" :title="detailError" type="error" show-icon :closable="false">
        <template #default><el-button link type="primary" @click="detailId && openDetail(detailId)">重试</el-button></template>
      </el-alert>
      <template v-else-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="批次">{{ detail.batchNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="素材类型">{{ detail.materialTypeName }}</el-descriptions-item>
          <el-descriptions-item label="模板版本ID">{{ detail.schemaVersionId }}</el-descriptions-item>
          <el-descriptions-item label="源文件">{{ detail.sourceFileName }}</el-descriptions-item>
          <el-descriptions-item label="预检时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="总行数 / 可导入 / 错误">{{ detail.totalCount }} / {{ detail.successCount }} / {{ detail.failureCount }}</el-descriptions-item>
          <el-descriptions-item label="确认时间">{{ formatTime(detail.confirmedAt) }}</el-descriptions-item>
        </el-descriptions>
        <div class="detail-actions">
          <el-button
            v-if="detail.status === 'PREVIEWED' && detail.successCount > 0"
            v-hasPermi="['zsjos:material-import:commit']"
            type="primary"
            :loading="committing"
            @click="commit(detail)"
          >确认导入合法行</el-button>
          <el-button
            v-if="detail.failureCount > 0"
            v-hasPermi="['zsjos:material-import:error-download']"
            :loading="reportDownloadingId === detail.id"
            @click="downloadErrors(detail)"
          >下载错误报告</el-button>
        </div>
        <el-table :data="detail.errors || []" empty-text="没有错误行">
          <el-table-column prop="sheetName" label="工作表" width="130" />
          <el-table-column prop="rowNo" label="行号" width="80" />
          <el-table-column prop="fieldKey" label="字段" min-width="150" />
          <el-table-column prop="errorCode" label="错误编码" min-width="150" />
          <el-table-column prop="errorMessage" label="问题" min-width="260" />
        </el-table>
      </template>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import type { UploadFile, UploadFiles, UploadInstance, UploadRawFile } from 'element-plus'
import download from '@/utils/download'
import * as MaterialApi from '@/api/zsjos/material'

defineOptions({ name: 'ZsjosMaterialImport' })

const message = useMessage()
const loading = ref(false)
const error = ref('')
const typeLoading = ref(false)
const typeError = ref('')
const typesLoaded = ref(false)
const types = ref<MaterialApi.MaterialType[]>([])
const rows = ref<MaterialApi.MaterialImportBatch[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 20, materialTypeId: undefined as number | undefined, status: undefined as string | undefined })
const downloading = ref(false)
const reportDownloadingId = ref<number>()
const committing = ref(false)

const importVisible = ref(false)
const importTypeId = ref<number>()
const uploadRef = ref<UploadInstance>()
const selectedFile = ref<File>()
const previewKey = ref('')
const previewing = ref(false)
const previewResult = ref<MaterialApi.MaterialImportBatch>()

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detailId = ref<number>()
const detail = ref<MaterialApi.MaterialImportBatch>()

const importTypes = computed(() => types.value.filter((type) => type.status === 0 && type.allowImport && type.currentSchema))
const typesReady = computed(() => typesLoaded.value && !typeError.value)
const statusLabel = (status: string) => ({ PREVIEWED: '已预检', COMMITTED: '已确认' }[status] || status)
const formatTime = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
const idempotencyKey = () => globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`

const loadTypes = async () => {
  typeLoading.value = true
  typeError.value = ''
  typesLoaded.value = false
  try {
    types.value = await MaterialApi.getMaterialTypeList()
    typesLoaded.value = true
  } catch (cause: any) {
    typeError.value = cause?.msg || cause?.message || '素材类型加载失败'
  } finally {
    typeLoading.value = false
  }
}

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const page = await MaterialApi.getMaterialImportPage(query)
    rows.value = page.list || []
    total.value = page.total || 0
  } catch (cause: any) {
    rows.value = []
    total.value = 0
    error.value = cause?.msg || cause?.message || '导入记录加载失败'
  } finally {
    loading.value = false
  }
}
const search = () => {
  query.pageNo = 1
  void load()
}
const resetQuery = () => {
  Object.assign(query, { pageNo: 1, pageSize: 20, materialTypeId: undefined, status: undefined })
  void load()
}
const requireType = () => {
  if (!typesReady.value) {
    message.warning('素材类型尚未加载完成')
    return undefined
  }
  const id = query.materialTypeId || importTypeId.value
  if (!id) {
    message.warning('请先选择素材类型')
    return undefined
  }
  return importTypes.value.find((type) => type.id === id)
}
const downloadTemplate = async () => {
  const type = requireType()
  if (!type) return
  downloading.value = true
  try {
    const blob = await MaterialApi.downloadMaterialImportTemplate(type.id)
    download.excel(blob, `${type.name}-素材导入模板.xlsx`)
  } finally {
    downloading.value = false
  }
}
const openImport = () => {
  if (!typesReady.value) return message.warning('素材类型尚未加载完成')
  importTypeId.value = query.materialTypeId
  resetSelectedFile()
  importVisible.value = true
}
const selectFile = (uploadFile: UploadFile, _uploadFiles: UploadFiles) => {
  const raw = uploadFile.raw as UploadRawFile | undefined
  if (!raw) return
  if (!raw.name.toLowerCase().endsWith('.xlsx')) {
    message.warning('只支持 .xlsx 文件')
    return resetSelectedFile()
  }
  if (raw.size > 20 * 1024 * 1024) {
    message.warning('导入文件不能超过 20 MB')
    return resetSelectedFile()
  }
  selectedFile.value = raw
  previewKey.value = idempotencyKey()
  previewResult.value = undefined
}
const clearSelectedFileState = () => {
  selectedFile.value = undefined
  previewKey.value = ''
  previewResult.value = undefined
}
const resetSelectedFile = () => {
  clearSelectedFileState()
  uploadRef.value?.clearFiles()
}
const handleRemove = () => clearSelectedFileState()
const preview = async () => {
  if (!importTypeId.value || !selectedFile.value) return
  previewing.value = true
  try {
    previewResult.value = await MaterialApi.previewMaterialImport(
      importTypeId.value,
      previewKey.value || idempotencyKey(),
      selectedFile.value
    )
    await load()
    message.success('预检完成')
  } catch (cause: any) {
    message.error(cause?.msg || cause?.message || '预检失败')
  } finally {
    previewing.value = false
  }
}
const commit = async (batch: MaterialApi.MaterialImportBatch) => {
  await useMessage().confirm(`确认导入 ${batch.successCount} 行合法素材？错误行会跳过。`)
  committing.value = true
  try {
    await MaterialApi.commitMaterialImport(batch.id, batch.version)
    message.success('合法素材已导入')
    if (previewResult.value?.id === batch.id) previewResult.value = await MaterialApi.getMaterialImport(batch.id)
    if (detail.value?.id === batch.id) detail.value = await MaterialApi.getMaterialImport(batch.id)
    await load()
  } catch (cause: any) {
    message.error(cause?.msg || cause?.message || '确认导入失败')
  } finally {
    committing.value = false
  }
}
const downloadErrors = async (batch: MaterialApi.MaterialImportBatch) => {
  reportDownloadingId.value = batch.id
  try {
    const blob = await MaterialApi.downloadMaterialImportErrors(batch.id)
    download.excel(blob, `${batch.batchNo}-错误报告.xlsx`)
  } finally {
    reportDownloadingId.value = undefined
  }
}
const openDetail = async (id: number) => {
  detailId.value = id
  detailVisible.value = true
  detailLoading.value = true
  detailError.value = ''
  try {
    detail.value = await MaterialApi.getMaterialImport(id)
  } catch (cause: any) {
    detail.value = undefined
    detailError.value = cause?.msg || cause?.message || '导入详情加载失败'
  } finally {
    detailLoading.value = false
  }
}

onMounted(() => Promise.all([loadTypes(), load()]))
</script>

<style scoped>
.material-import-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.detail-actions {
  display: flex;
  gap: 12px;
  margin: 16px 0;
}

@media (max-width: 768px) {
  .material-import-toolbar {
    flex-direction: column;
  }
}
</style>
