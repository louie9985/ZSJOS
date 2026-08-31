<template>
  <div class="notice-editor-page">
    <div class="notice-editor-toolbar">
      <el-button text @click="emit('back')"><Icon icon="ep:arrow-left" /> 返回公告列表</el-button>
      <div class="notice-editor-actions">
        <el-button @click="previewVisible = true"><Icon icon="ep:view" /> 预览</el-button>
        <el-button :loading="saving" @click="saveDraft">保存草稿</el-button>
        <el-button
          type="primary"
          :loading="publishing"
          v-hasPermi="['system:notice:publish']"
          @click="publish"
        >发布</el-button>
      </div>
    </div>

    <div class="notice-editor-body" v-loading="loading">
      <el-form ref="formRef" :model="formData" :rules="rules" label-position="top">
        <el-form-item label="公告标题" prop="title">
          <el-input v-model="formData.title" maxlength="50" show-word-limit placeholder="请输入公告标题" />
        </el-form-item>
        <el-form-item label="公告类型" prop="type">
          <el-select v-model="formData.type" placeholder="请选择公告类型" class="notice-editor-type">
            <el-option
              v-for="dict in getIntDictOptions(DICT_TYPE.SYSTEM_NOTICE_TYPE)"
              :key="dict.value"
              :label="dict.label"
              :value="Number(dict.value)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="高亮提醒截止时间" prop="highlightUntil">
          <el-date-picker v-model="formData.highlightUntil" type="datetime" value-format="x" clearable placeholder="不设置则不高亮" :disabled-date="disabledHighlightDate" />
          <div class="el-form-item__tip">截止时间前将在员工首页公告栏优先置顶</div>
        </el-form-item>
        <el-form-item label="正文" prop="content">
          <Editor v-model="formData.content" height="480px" directory="system-notice-content" />
        </el-form-item>
        <el-form-item label="附件">
          <el-upload
            drag
            multiple
            :show-file-list="false"
            :http-request="uploadAttachment"
            :before-upload="beforeUpload"
            :disabled="formData.attachments.length + activeUploadCount >= 10"
          >
            <Icon icon="ep:upload-filled" :size="28" />
            <div>拖拽文件到此处，或点击上传</div>
            <template #tip>
              <div class="el-upload__tip">最多 10 个，支持图片、Office、PDF 和 ZIP</div>
            </template>
          </el-upload>
          <div v-if="uploadTasks.length" class="notice-attachment-list">
            <div v-for="task in uploadTasks" :key="task.uid" class="notice-attachment-row">
              <Icon :icon="getFileIcon(task.name)" class="notice-attachment-icon" />
              <span class="notice-attachment-name">{{ task.name }}</span>
              <el-progress
                v-if="task.status === 'uploading'"
                class="notice-upload-progress"
                :percentage="task.progress"
                :stroke-width="6"
              />
              <span v-else class="notice-upload-error">{{ task.error }}</span>
              <el-button v-if="task.status === 'error'" link type="danger" @click="removeUploadTask(task.uid)">移除</el-button>
            </div>
          </div>
          <div v-if="formData.attachments.length" class="notice-attachment-list">
            <div v-for="(file, index) in formData.attachments" :key="file.infraFileId" class="notice-attachment-row">
              <Icon :icon="getFileIcon(file.fileName, file.mimeType)" class="notice-attachment-icon" />
              <span class="notice-attachment-name">{{ file.fileName }}</span>
              <span class="notice-attachment-size">{{ formatFileSize(file.fileSize) }}</span>
              <el-link v-if="file.downloadUrl" :href="file.downloadUrl" target="_blank">预览</el-link>
              <el-button link type="danger" @click="formData.attachments.splice(index, 1)">删除</el-button>
            </div>
          </div>
        </el-form-item>
      </el-form>
    </div>

    <el-drawer v-model="previewVisible" title="公告预览" size="720px">
      <article class="notice-preview">
        <h1>{{ formData.title || '未填写标题' }}</h1>
        <Editor v-model="formData.content" readonly height="auto" />
        <section v-if="formData.attachments.length" class="notice-preview-files">
          <h3>附件</h3>
          <div v-for="file in formData.attachments" :key="file.infraFileId">
            <Icon :icon="getFileIcon(file.fileName, file.mimeType)" class="notice-attachment-icon" /> {{ file.fileName }} · {{ formatFileSize(file.fileSize) }}
          </div>
        </section>
      </article>
    </el-drawer>
  </div>
</template>

<script lang="ts" setup>
import type {
  FormInstance,
  FormRules,
  UploadProgressEvent,
  UploadRawFile,
  UploadRequestOptions
} from 'element-plus'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import { getFileIcon } from '@/utils/file'
import * as NoticeApi from '@/api/system/notice'

const props = defineProps<{ id?: number }>()
const emit = defineEmits<{ back: []; saved: [id: number] }>()
const message = useMessage()
const formRef = ref<FormInstance>()
const loading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const previewVisible = ref(false)
const noticeId = ref<number | undefined>(props.id)
const formData = reactive({
  title: '',
  type: undefined as number | undefined,
  content: '',
  status: 0,
  highlightUntil: undefined as number | undefined,
  attachments: [] as NoticeApi.NoticeAttachmentVO[]
})
const rules: FormRules = {
  title: [{ required: true, message: '公告标题不能为空', trigger: 'blur' }],
  type: [{ required: true, message: '公告类型不能为空', trigger: 'change' }],
  content: [{ required: true, message: '公告正文不能为空', trigger: 'blur' }]
}
type UploadTask = {
  uid: number
  name: string
  progress: number
  status: 'uploading' | 'error'
  error?: string
}
const uploadTasks = ref<UploadTask[]>([])
const activeUploadCount = computed(
  () => uploadTasks.value.filter((task) => task.status === 'uploading').length
)

const load = async () => {
  if (!props.id) return
  loading.value = true
  try {
    const data = await NoticeApi.getNotice(props.id) as NoticeApi.NoticeVO
    formData.title = data.title
    formData.type = data.type
    formData.content = data.content
    formData.highlightUntil = data.highlightUntil
    formData.attachments = data.attachments || []
  } finally {
    loading.value = false
  }
}

const saveDraft = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    const payload = { ...formData, id: noticeId.value } as NoticeApi.NoticeVO
    if (noticeId.value) await NoticeApi.updateNotice(payload)
    else noticeId.value = await NoticeApi.createNotice(payload)
    message.success('草稿已保存')
    emit('saved', noticeId.value!)
    return noticeId.value
  } finally {
    saving.value = false
  }
}

const publish = async () => {
  publishing.value = true
  try {
    const id = await saveDraft()
    if (!id) return
    await message.confirm('发布后内容不可直接修改，确认发布该公告？')
    await NoticeApi.publishNotice(id)
    message.success('公告已发布')
    emit('back')
  } finally {
    publishing.value = false
  }
}

const disabledHighlightDate = (date: Date) => date.getTime() < Date.now() - 60 * 1000

const allowedExtensions = ['png', 'jpg', 'jpeg', 'gif', 'webp', 'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'zip']
const beforeUpload = (file: UploadRawFile) => {
  const extension = file.name.split('.').pop()?.toLowerCase() || ''
  if (formData.attachments.length + activeUploadCount.value >= 10) { message.error('公告附件不能超过 10 个'); return false }
  if (!allowedExtensions.includes(extension)) { message.error('不支持该文件格式'); return false }
  return true
}

const uploadAttachment = async (options: UploadRequestOptions) => {
  const task: UploadTask = { uid: options.file.uid, name: options.file.name, progress: 0, status: 'uploading' }
  uploadTasks.value.push(task)
  try {
    const response = await NoticeApi.uploadNoticeAttachment(options.file, (event) => {
      task.progress = Math.round((event.progress || 0) * 100)
      const progressEvent = new ProgressEvent('progress', {
        lengthComputable: Boolean(event.total),
        loaded: event.loaded,
        total: event.total || 0
      }) as UploadProgressEvent
      progressEvent.percent = task.progress
      options.onProgress?.(progressEvent)
    })
    if (response.code !== 0) throw new Error(response.msg || '附件上传失败')
    formData.attachments.push({ ...response.data, sort: formData.attachments.length })
    options.onSuccess?.(response)
    removeUploadTask(task.uid)
    message.success('附件上传成功')
  } catch (error) {
    task.status = 'error'
    task.error = error instanceof Error ? error.message : '上传失败'
    options.onError?.(error as any)
    message.error(task.error)
  }
}

const removeUploadTask = (uid: number) => {
  uploadTasks.value = uploadTasks.value.filter((task) => task.uid !== uid)
}

const formatFileSize = (size: number) => size >= 1024 * 1024
  ? `${(size / 1024 / 1024).toFixed(1)} MB`
  : `${Math.max(1, Math.round(size / 1024))} KB`

onMounted(load)
</script>

<style scoped>
.notice-editor-page { min-height: 100%; background: var(--el-bg-color-page); }
.notice-editor-toolbar { position: sticky; top: 0; z-index: 20; display: flex; justify-content: space-between; align-items: center; min-height: 56px; padding: 0 24px; background: var(--el-bg-color); border-bottom: 1px solid var(--el-border-color-light); }
.notice-editor-actions { display: flex; gap: 8px; }
.notice-editor-body { width: min(1120px, calc(100% - 32px)); margin: 24px auto; padding: 24px; background: var(--el-bg-color); border-radius: 8px; }
.notice-editor-type { width: 240px; }
.notice-editor-body :deep(.el-upload-dragger) { width: 100%; }
.notice-editor-body :deep(.el-upload) { width: 100%; }
.notice-attachment-list { width: 100%; margin-top: 12px; }
.notice-attachment-row { display: flex; align-items: center; gap: 10px; min-height: 44px; padding: 0 12px; background: var(--el-fill-color-light); border-radius: 6px; }
.notice-attachment-row + .notice-attachment-row { margin-top: 8px; }
.notice-attachment-icon { flex: 0 0 auto; color: var(--el-text-color-secondary); }
.notice-attachment-name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.notice-attachment-size { color: var(--el-text-color-secondary); }
.notice-upload-progress { width: 180px; }
.notice-upload-error { max-width: 240px; overflow: hidden; color: var(--el-color-danger); text-overflow: ellipsis; white-space: nowrap; }
.notice-preview { max-width: 680px; margin: 0 auto; }
.notice-preview h1 { margin: 0 0 24px; font-size: 24px; letter-spacing: 0; }
.notice-preview-files { margin-top: 24px; padding-top: 16px; border-top: 1px solid var(--el-border-color-light); }
@media (max-width: 768px) {
  .notice-editor-toolbar { padding: 8px 12px; align-items: flex-start; gap: 8px; }
  .notice-editor-actions { flex-wrap: wrap; justify-content: flex-end; }
  .notice-editor-body { width: calc(100% - 16px); margin: 8px auto; padding: 12px; }
  .notice-editor-type { width: 100%; }
  .notice-attachment-row { align-items: flex-start; flex-wrap: wrap; padding: 10px 12px; }
  .notice-upload-progress { width: 120px; }
}
</style>
