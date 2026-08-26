import { ref } from 'vue'
import { showToast } from 'vant'
import { uploadLeadAttachment, type UploadResult } from '@/api/lead'
import { createIdempotencyKey } from '@/utils/idempotency'

export interface UploadFile {
  id: string
  file: File
  url: string
  status: 'uploading' | 'done' | 'error'
  result?: UploadResult
  error?: string
}

/**
 * 图片上传 composable
 */
export function useUpload(maxCount = 9) {
  const fileList = ref<UploadFile[]>([])
  const uploading = ref(false)

  async function addFile(file: File) {
    if (fileList.value.length >= maxCount) {
      showToast(`最多上传 ${maxCount} 张图片`)
      return
    }

    const id = createIdempotencyKey()
    const url = URL.createObjectURL(file)
    const item: UploadFile = { id, file, url, status: 'uploading' }
    fileList.value.push(item)

    uploading.value = true
    try {
      const result = await uploadLeadAttachment(file)
      item.status = 'done'
      item.result = result
      item.error = undefined
      URL.revokeObjectURL(url)
      item.url = result.fileUrl
    } catch (cause) {
      item.status = 'error'
      item.error = cause instanceof Error ? cause.message : '图片上传失败'
    } finally {
      uploading.value = fileList.value.some(f => f.status === 'uploading')
    }
  }

  function removeFile(id: string) {
    const idx = fileList.value.findIndex(f => f.id === id)
    if (idx >= 0) {
      const item = fileList.value[idx]
      if (item.status === 'uploading') {
        showToast('图片正在上传，请稍候')
        return
      }
      if (item.url.startsWith('blob:')) {
        URL.revokeObjectURL(item.url)
      }
      fileList.value.splice(idx, 1)
    }
  }

  async function retryFile(id: string) {
    const item = fileList.value.find(f => f.id === id)
    if (!item) return

    item.status = 'uploading'
    item.result = undefined
    item.error = undefined
    uploading.value = true
    try {
      const result = await uploadLeadAttachment(item.file)
      item.status = 'done'
      item.result = result
      if (item.url.startsWith('blob:')) URL.revokeObjectURL(item.url)
      item.url = result.fileUrl
    } catch (cause) {
      item.status = 'error'
      item.error = cause instanceof Error ? cause.message : '图片上传失败'
    } finally {
      uploading.value = fileList.value.some(f => f.status === 'uploading')
    }
  }

  function getUploadedIds(): number[] {
    return fileList.value
      .filter(f => f.status === 'done' && f.result)
      .map(f => f.result!.infraFileId)
  }

  function hasError(): boolean {
    return fileList.value.some(f => f.status === 'error')
  }

  function reset() {
    fileList.value.forEach(f => {
      if (f.url.startsWith('blob:')) URL.revokeObjectURL(f.url)
    })
    fileList.value = []
    uploading.value = false
  }

  function getUploadedFiles(): UploadResult[] {
    return fileList.value
      .filter(f => f.status === 'done' && f.result)
      .map(f => f.result!)
  }

  return {
    fileList,
    uploading,
    addFile,
    removeFile,
    retryFile,
    getUploadedIds,
    getUploadedFiles,
    hasError,
    reset
  }
}
