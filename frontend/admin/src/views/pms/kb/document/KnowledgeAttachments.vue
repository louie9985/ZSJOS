<template>
  <section v-if="files.length" class="mt-20px mb-16px">
    <h3>附件</h3>
    <div
      v-for="file in files"
      :key="file.fileId"
      class="mb-8px flex flex-wrap items-center gap-8px"
    >
      <span class="break-all">{{ file.fileName }}</span>
      <el-text type="info">{{ formatKnowledgeFileSize(file.fileSize) }}</el-text>
      <el-button :loading="busy === file.fileId" link type="primary" @click="open(file)"
        >预览／下载</el-button
      >
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <Dialog v-model="visible" title="附件" width="min(900px, 94vw)">
      <AttachmentItem v-if="selected && url" :url="url" :name="selected.fileName" />
    </Dialog>
  </section>
</template>
<script setup lang="ts">
import AttachmentItem from '@/components/UploadFile/src/AttachmentItem.vue'
import { knowledgeAttachmentUrl, type KnowledgeAttachment } from '@/api/pms/kb/content/document'
import { formatKnowledgeFileSize } from '@/views/pms/kb/utils/format'
const props = defineProps<{ files: KnowledgeAttachment[]; documentId: number; recordId?: number }>()
const selected = ref<KnowledgeAttachment>()
const url = ref('')
const visible = ref(false)
const busy = ref<number>()
const error = ref('')
async function open(file: KnowledgeAttachment) {
  busy.value = file.fileId
  error.value = ''
  try {
    url.value =
      file.url || (await knowledgeAttachmentUrl(props.documentId, file.fileId, props.recordId))
    selected.value = file
    visible.value = true
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '附件暂时无法访问，请重试或联系文档编辑者'
  } finally {
    busy.value = undefined
  }
}
</script>
