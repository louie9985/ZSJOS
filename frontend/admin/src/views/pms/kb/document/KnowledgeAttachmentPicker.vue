<template>
  <section class="mt-20px">
    <h3>附件</h3>
    <ClipboardUploadActions
      :disabled="disabled"
      :can-paste="items.length + tasks.length < 10"
      @files="addFiles"
    >
      <el-upload
        multiple
        :show-file-list="false"
        :disabled="disabled"
        :auto-upload="false"
        :on-change="choose"
        accept=".png,.jpg,.jpeg,.gif,.webp,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.zip"
      >
        <el-button :disabled="disabled">上传附件（最多 10 个）</el-button>
      </el-upload>
    </ClipboardUploadActions>
    <el-text type="info"
      >支持图片、Office、PDF 和 ZIP，单个不超过 100 MB；上传完成后可保存。</el-text
    >
    <div
      v-for="file in items"
      :key="file.fileId"
      class="mt-8px flex flex-wrap items-center gap-8px"
    >
      <span class="break-all">{{ file.fileName }}</span>
      <el-text type="info">{{ formatKnowledgeFileSize(file.fileSize) }}</el-text>
      <el-button :disabled="disabled" link type="danger" @click="remove(file.fileId)"
        >移除</el-button
      >
    </div>
    <div v-for="task in tasks" :key="task.id" class="mt-8px">
      <span class="break-all">{{ task.file.name }}</span>
      <el-progress v-if="!task.error" :percentage="task.progress" />
      <template v-else>
        <el-text type="danger">{{ task.error }}</el-text>
        <el-button :disabled="disabled" link @click="run(task)">重试</el-button>
        <el-button :disabled="disabled" link @click="discard(task.id)">移除失败项</el-button>
      </template>
    </div>
  </section>
</template>
<script setup lang="ts">
import type { UploadFile } from 'element-plus'
import ClipboardUploadActions from '@/components/UploadFile/src/ClipboardUploadActions.vue'
import { uploadKnowledgeAttachment, type KnowledgeAttachment } from '@/api/pms/kb/content/document'
import { formatKnowledgeFileSize } from '@/views/pms/kb/utils/format'
const props = defineProps<{
  modelValue: KnowledgeAttachment[]
  documentId: number
  disabled?: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [KnowledgeAttachment[]]; blocked: [boolean] }>()
type Task = { id: number; file: File; progress: number; error: string }
const tasks = ref<Task[]>([])
const items = computed(() => props.modelValue)
const message = useMessage()
let sequence = 0
let disposed = false
let queue = Promise.resolve()
const extensions = new Set([
  'png',
  'jpg',
  'jpeg',
  'gif',
  'webp',
  'pdf',
  'doc',
  'docx',
  'xls',
  'xlsx',
  'ppt',
  'pptx',
  'zip'
])
watch(
  () => tasks.value.length,
  (value) => emit('blocked', value > 0)
)
onBeforeUnmount(() => {
  disposed = true
  emit('blocked', false)
})
function choose(file: UploadFile) {
  if (file.raw) addFiles([file.raw])
}
function addFiles(files: File[]) {
  if (props.disabled) return
  for (const file of files) {
    if (items.value.length + tasks.value.length >= 10) {
      message.error('附件不能超过 10 个')
      break
    }
    if (
      !file.size ||
      file.size > 100 * 1024 * 1024 ||
      !extensions.has(file.name.split('.').pop()?.toLowerCase() || '')
    ) {
      message.error('附件格式不支持、为空或超过 100 MB')
      continue
    }
    const task = reactive<Task>({ id: ++sequence, file, progress: 0, error: '' })
    tasks.value.push(task)
    queue = queue.then(() => run(task))
  }
}
async function run(task: Task) {
  task.error = ''
  task.progress = 0
  try {
    const file = await uploadKnowledgeAttachment(props.documentId, task.file, (value) => {
      task.progress = value
    })
    if (disposed) return
    emit('update:modelValue', [...items.value, file])
    await nextTick()
    tasks.value = tasks.value.filter((item) => item.id !== task.id)
  } catch (cause) {
    task.error = cause instanceof Error ? cause.message + '，请重试或移除' : '上传失败，请重试或移除'
  }
}
function discard(id: number) {
  tasks.value = tasks.value.filter((task) => task.id !== id)
}
function remove(id: number) {
  emit(
    'update:modelValue',
    items.value.filter((file) => file.fileId !== id)
  )
}
</script>
