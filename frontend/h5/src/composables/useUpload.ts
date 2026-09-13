import { computed, onUnmounted, ref } from 'vue'
import { showToast } from 'vant'
import { uploadLeadAttachment, type UploadResult } from '@/api/lead'
import { createIdempotencyKey } from '@/utils/idempotency'

export interface UploadFile {
  id: string
  file: File
  url: string
  status: 'queued' | 'uploading' | 'processing' | 'done' | 'error'
  progress: number
  result?: UploadResult
  error?: string
}

/**
 * 图片上传 composable
 */
export function useUpload(maxCount = 9) {
  const fileList = ref<UploadFile[]>([])
  const controllers = new Map<string, AbortController>()
  const uploading = computed(() => fileList.value.some(file => (
    file.status === 'queued' || file.status === 'uploading' || file.status === 'processing'
  )))
  let queueRunning = false

  function addFile(file: File) {
    if (fileList.value.length >= maxCount) {
      showToast(`最多上传 ${maxCount} 张图片`)
      return
    }

    const id = createIdempotencyKey()
    const url = URL.createObjectURL(file)
    const item: UploadFile = { id, file, url, status: 'queued', progress: 0 }
    fileList.value.push(item)
    void runQueue()
  }

  async function uploadItem(item: UploadFile) {
    const controller = new AbortController()
    controllers.set(item.id, controller)
    item.status = 'uploading'
    item.progress = 0
    try {
      const result = await uploadLeadAttachment(item.file, {
        signal: controller.signal,
        onProgress: (progress) => {
          if (!fileList.value.some(file => file.id === item.id)) return
          item.progress = progress
          item.status = progress >= 100 ? 'processing' : 'uploading'
        }
      })
      if (!fileList.value.some(file => file.id === item.id)) return
      item.status = 'done'
      item.progress = 100
      item.result = result
      item.error = undefined
      if (item.url.startsWith('blob:')) URL.revokeObjectURL(item.url)
      item.url = result.fileUrl
    } catch (cause) {
      if (!fileList.value.some(file => file.id === item.id) || controller.signal.aborted) return
      item.status = 'error'
      item.progress = 0
      item.error = uploadErrorMessage(cause)
    } finally {
      controllers.delete(item.id)
    }
  }

  async function runQueue() {
    if (queueRunning) return
    queueRunning = true
    try {
      let next = fileList.value.find(file => file.status === 'queued')
      while (next) {
        await uploadItem(next)
        next = fileList.value.find(file => file.status === 'queued')
      }
    } finally {
      queueRunning = false
      if (fileList.value.some(file => file.status === 'queued')) void runQueue()
    }
  }

  function uploadErrorMessage(cause: unknown) {
    if (!(cause instanceof Error)) return '图片上传失败，请重试'
    if (/timeout|timed out|ECONNABORTED/i.test(cause.message)) return '上传超时，请重试'
    return cause.message || '图片上传失败，请重试'
  }

  function removeFile(id: string) {
    const idx = fileList.value.findIndex(f => f.id === id)
    if (idx >= 0) {
      const item = fileList.value[idx]
      controllers.get(id)?.abort()
      if (item.url.startsWith('blob:')) {
        URL.revokeObjectURL(item.url)
      }
      fileList.value.splice(idx, 1)
    }
  }

  async function retryFile(id: string) {
    const item = fileList.value.find(f => f.id === id)
    if (!item || item.status !== 'error') return

    item.status = 'queued'
    item.progress = 0
    item.result = undefined
    item.error = undefined
    await runQueue()
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
    controllers.forEach(controller => controller.abort())
    controllers.clear()
    fileList.value.forEach(f => {
      if (f.url.startsWith('blob:')) URL.revokeObjectURL(f.url)
    })
    fileList.value = []
  }

  function getUploadedFiles(): UploadResult[] {
    return fileList.value
      .filter(f => f.status === 'done' && f.result)
      .map(f => f.result!)
  }

  onUnmounted(reset)

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
