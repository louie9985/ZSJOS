<template>
  <Dialog v-model="dialogVisible" title="导入资产" width="560px">
    <el-upload
      ref="uploadRef"
      :action="importUrl"
      :headers="uploadHeaders"
      :auto-upload="false"
      :limit="1"
      :on-exceed="handleExceed"
      :on-success="handleSuccess"
      :on-error="handleError"
      :disabled="uploading"
      accept=".xlsx,.xls"
      drag
    >
      <Icon icon="ep:upload" />
      <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
      <template #tip>
        <div class="el-upload__tip text-center">
          <div class="mb-1">仅支持 xls / xlsx 文件</div>
          <div>
            <el-link type="primary" :underline="false" @click="downloadTemplate">
              下载导入模板
            </el-link>
          </div>
        </div>
      </template>
    </el-upload>

    <el-alert
      class="mt-3"
      title="分类请填写「分类编码」而非名称；分类自定义字段需导入后到资产详情补录"
      type="info"
      :closable="false"
      show-icon
    />

    <!-- 导入结果 -->
    <template v-if="result">
      <el-divider content-position="left">
        <span class="text-sm text-gray-500">导入结果</span>
      </el-divider>
      <div class="mb-2">
        成功
        <span class="font-medium text-green-600">{{ result.createAssetCodes.length }}</span>
        条，失败
        <span class="font-medium text-red-500">{{ result.failures.length }}</span>
        条
      </div>
      <el-table v-if="result.failures.length > 0" :data="result.failures" max-height="220" border>
        <el-table-column label="行号" prop="rowNum" width="70" align="center" />
        <el-table-column label="资产名称" prop="name" min-width="140" show-overflow-tooltip />
        <el-table-column label="失败原因" prop="reason" min-width="200" show-overflow-tooltip />
      </el-table>
    </template>

    <template #footer>
      <el-button :loading="uploading" type="primary" @click="submitUpload">开始导入</el-button>
      <el-button @click="dialogVisible = false">关 闭</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { getAccessToken, getTenantId } from '@/utils/auth'
import download from '@/utils/download'
import * as AssetApi from '@/api/eam/asset'

defineOptions({ name: 'EamAssetImportForm' })

const emit = defineEmits(['success'])

const message = useMessage()

const dialogVisible = ref(false)
const uploading = ref(false)
const uploadRef = ref()
const result = ref<AssetApi.AssetImportRespVO>()

const importUrl = AssetApi.getImportUrl()
const uploadHeaders = ref({
  Authorization: 'Bearer ' + getAccessToken(),
  'tenant-id': getTenantId()
})

const open = () => {
  dialogVisible.value = true
  result.value = undefined
  uploadRef.value?.clearFiles()
}
defineExpose({ open })

const submitUpload = () => {
  uploading.value = true
  uploadRef.value.submit()
}

const handleExceed = () => {
  message.error('每次只能导入一个文件')
}

const handleSuccess = (response: any) => {
  uploading.value = false
  if (response.code !== 0) {
    message.error(response.msg)
    return
  }
  result.value = response.data
  uploadRef.value?.clearFiles()

  // 有失败行时留在弹窗让使用者查看原因，全部成功才提示并关闭
  if (result.value!.failures.length === 0) {
    message.success(`导入成功 ${result.value!.createAssetCodes.length} 条`)
  } else {
    message.warning(
      `导入完成：成功 ${result.value!.createAssetCodes.length} 条，失败 ${result.value!.failures.length} 条`
    )
  }
  emit('success')
}

const handleError = () => {
  uploading.value = false
  message.error('导入失败，请检查文件格式')
}

const downloadTemplate = async () => {
  const data = await AssetApi.importTemplate()
  download.excel(data, '资产导入模板.xlsx')
}
</script>
