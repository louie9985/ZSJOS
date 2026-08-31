<template>
  <Dialog v-model="dialogVisible" title="资产二维码" width="380px">
    <div ref="printAreaRef" class="flex flex-col items-center gap-3 py-2">
      <div class="flex h-[240px] w-[240px] items-center justify-center border border-gray-200">
        <img v-if="qrSrc" :src="qrSrc" alt="资产二维码" class="h-full w-full" />
        <el-text v-else-if="qrLoading">加载中...</el-text>
        <el-text v-else type="danger">二维码加载失败</el-text>
      </div>
      <div class="text-center">
        <div class="text-base font-medium">{{ asset.name }}</div>
        <div class="mt-1 font-mono text-sm text-gray-600">{{ asset.assetCode }}</div>
      </div>
    </div>

    <template #footer>
      <el-button type="primary" @click="handlePrint">
        <Icon icon="ep:printer" class="mr-5px" /> 打 印
      </el-button>
      <el-button v-if="qrError" @click="loadQrCode">重 试</el-button>
      <el-button @click="dialogVisible = false">关 闭</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import * as AssetApi from '@/api/eam/asset'

defineOptions({ name: 'EamAssetQrCodeDialog' })

const dialogVisible = ref(false)
const asset = ref<AssetApi.AssetVO>({} as AssetApi.AssetVO)
const printAreaRef = ref<HTMLElement>()
const qrSrc = ref('')
const qrLoading = ref(false)
const qrError = ref(false)

const revokeQrSrc = () => {
  if (qrSrc.value) {
    URL.revokeObjectURL(qrSrc.value)
    qrSrc.value = ''
  }
}

const loadQrCode = async () => {
  if (!asset.value.id) {
    return
  }
  revokeQrSrc()
  qrError.value = false
  qrLoading.value = true
  try {
    const blob = await AssetApi.downloadQrCode(asset.value.id)
    qrSrc.value = URL.createObjectURL(blob)
  } catch {
    qrError.value = true
  } finally {
    qrLoading.value = false
  }
}

const open = (row: AssetApi.AssetVO) => {
  asset.value = row
  dialogVisible.value = true
  loadQrCode()
}
defineExpose({ open })

watch(dialogVisible, (visible) => {
  if (!visible) {
    revokeQrSrc()
    qrError.value = false
    qrLoading.value = false
  }
})

onBeforeUnmount(revokeQrSrc)

/** 只打印标签区域，避免带出后台页面的导航和表格 */
const handlePrint = () => {
  const content = printAreaRef.value?.innerHTML
  if (!content || !qrSrc.value) {
    return
  }
  const win = window.open('', '_blank', 'width=420,height=520')
  if (!win) {
    return
  }
  win.document.write(`
    <html>
      <head>
        <title>${asset.value.assetCode ?? '资产标签'}</title>
        <style>
          body { margin: 0; padding: 16px; font-family: system-ui, sans-serif; text-align: center; }
          img { width: 240px; height: 240px; border: 1px solid #e5e7eb; }
          .name { font-size: 16px; font-weight: 500; margin-top: 12px; }
          .code { font-family: monospace; font-size: 14px; color: #4b5563; margin-top: 4px; }
        </style>
      </head>
      <body>${content}</body>
    </html>
  `)
  win.document.close()
  // 等图片解码完成再触发打印，否则可能打出空白标签
  const img = win.document.querySelector('img')
  if (img && !img.complete) {
    img.onload = () => win.print()
    img.onerror = () => win.print()
  } else {
    win.print()
  }
}
</script>
