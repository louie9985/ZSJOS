<template>
  <Dialog
    v-model="dialogVisible"
    :before-close="beforeClose"
    default-fullscreen
    fullscreen
    width="min(1400px, calc(100vw - 24px))"
    title="编辑文档"
  >
    <template #title>
      <div class="w-full flex flex-wrap items-center justify-between gap-8px pr-64px">
        <span>编辑文档</span>
        <div class="flex flex-wrap items-center gap-8px">
          <el-button
            :loading="formLoading"
            :disabled="attachmentBlocked"
            type="primary"
            @click="submitForm"
            >保存</el-button
          >
          <el-button class="!ml-0" :disabled="attachmentBlocked" @click="previewing = !previewing">
            {{ previewing ? '返回编辑' : '预览' }}
          </el-button>
          <el-button
            class="!ml-0"
            @click="
              beforeClose(() => {
                dialogVisible = false
              })
            "
            >取消</el-button
          >
        </div>
      </div>
    </template>
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="0"
      class="w-full"
    >
      <el-form-item class="!mb-16px" prop="title">
        <el-input v-model="formData.title" maxlength="255" placeholder="请输入文档标题" />
      </el-form-item>
      <el-form-item class="!mb-16px" label="标签">
        <KnowledgeDocumentLabelSelect v-model="formData.labelIds" />
      </el-form-item>
      <el-form-item
        v-if="formData.type === PmsKnowledgeDocumentType.RICH_TEXT"
        class="!mb-0"
        prop="content"
      >
        <div
          v-if="previewing"
          class="pms-knowledge-rich-text"
          v-dompurify-html="formData.content || '<p>暂无内容</p>'"
        ></div>
        <div v-else class="w-full min-w-0">
          <Editor class="w-full" v-model="formData.content" height="min(55vh, 520px)" />
        </div>
      </el-form-item>
      <el-form-item v-else class="!mb-0" prop="content">
        <UploadFile
          v-model="formData.content"
          :file-size="PmsKnowledgeUploadFileSize"
          :limit="1"
          @update:model-value="handleFileChange"
          @update:file-size="handleFileSizeChange"
        />
      </el-form-item>
      <template v-if="formData.type === PmsKnowledgeDocumentType.RICH_TEXT && formData.id">
        <KnowledgeAttachments
          v-if="previewing"
          :files="formData.attachments"
          :document-id="formData.id"
        />
        <KnowledgeAttachmentPicker
          v-else
          v-model="formData.attachments"
          :document-id="formData.id"
          :disabled="formLoading"
          @blocked="attachmentBlocked = $event"
        />
      </template>
    </el-form>
  </Dialog>
</template>

<script lang="ts" setup>
import KnowledgeAttachmentPicker from './KnowledgeAttachmentPicker.vue'
import KnowledgeAttachments from './KnowledgeAttachments.vue'
import * as KnowledgeDocumentApi from '@/api/pms/kb/content/document'
import {
  PmsKnowledgeDocumentType,
  PmsKnowledgeUploadFileSize
} from '@/views/pms/kb/utils/constants'
import KnowledgeDocumentLabelSelect from './components/KnowledgeDocumentLabelSelect.vue'

defineOptions({ name: 'PmsKnowledgeDocumentUpdateForm' })

const message = useMessage() // 消息弹窗
const dialogVisible = ref(false) // 弹窗是否显示
const formLoading = ref(false) // 表单提交中
const previewing = ref(false) // 富文本只读预览
const attachmentBlocked = ref(false)
function beforeClose(done: () => void) {
  if (formLoading.value || attachmentBlocked.value) {
    message.info('请先等待上传完成或移除失败项')
    return
  }
  done()
}
const formData = ref(getDefaultFormData()) // 表单数据
const formRules = reactive({
  content: [{ required: true, message: '请输入文档内容', trigger: 'blur' }]
}) // 表单校验规则
const formRef = ref() // 表单 Ref
const emit = defineEmits(['success']) // 定义 success 事件，用于操作成功后的回调

/** 打开弹窗 */
async function open(id: number) {
  dialogVisible.value = true
  previewing.value = false
  formData.value = getDefaultFormData()
  formRef.value?.resetFields()
  formLoading.value = true
  try {
    // 1. 查询文档详情
    const document = await KnowledgeDocumentApi.getKnowledgeDocument(id)
    // 2. 初始化文档表单
    formData.value = {
      id: document.id,
      attachments: document.attachments || [],
      title: document.title,
      content: document.content || document.previewUrl || '',
      type: document.type,
      labelIds: document.labelIds ?? [],
      fileType: document.fileType,
      fileSize: document.fileSize
    }
  } finally {
    formLoading.value = false
  }
}
defineExpose({ open }) // 提供 open 方法，用于打开弹窗

/** 提交表单 */
async function submitForm() {
  // 校验表单
  if (!formRef.value || attachmentBlocked.value || formLoading.value) return
  const valid = await formRef.value.validate()
  if (!valid) return
  // 提交请求
  formLoading.value = true
  try {
    await KnowledgeDocumentApi.updateKnowledgeDocument({
      id: formData.value.id!,
      attachmentFileIds:
        formData.value.type === PmsKnowledgeDocumentType.RICH_TEXT
          ? formData.value.attachments.map((file) => file.fileId)
          : undefined,
      title: formData.value.title,
      content: formData.value.content,
      labelIds: formData.value.labelIds,
      fileType: formData.value.fileType,
      fileSize: formData.value.fileSize
    })
    message.success('更新成功')
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

/** 获得表单默认数据 */
function getDefaultFormData() {
  return {
    id: undefined as number | undefined,
    attachments: [] as KnowledgeDocumentApi.KnowledgeAttachment[],
    title: '',
    content: '',
    type: PmsKnowledgeDocumentType.RICH_TEXT as number,
    labelIds: [] as number[],
    fileType: undefined as string | undefined,
    fileSize: undefined as number | undefined
  }
}

/** 文件重新上传后更新文件大小元数据 */
function handleFileSizeChange(value?: number) {
  formData.value.fileSize = value
}

/** 文件替换后同步扩展名元数据 */
function handleFileChange(value: string) {
  if (!value) return
  const fileName = decodeURIComponent(value.split('?')[0].split('/').pop() || '')
  formData.value.fileType = fileName.includes('.')
    ? fileName.split('.').pop()?.toLowerCase()
    : undefined
}
</script>
