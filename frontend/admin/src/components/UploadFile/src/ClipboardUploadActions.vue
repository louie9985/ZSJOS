<template>
  <div class="clipboard-upload-target" :aria-busy="readingClipboard">
    <div class="clipboard-upload-actions">
      <slot></slot>
      <el-button
        :disabled="unavailable || readingClipboard"
        :loading="readingClipboard"
        aria-label="上传剪贴板截图"
        :aria-busy="readingClipboard"
        @click="uploadClipboardImages"
      >
        <Icon icon="ep:picture" class="mr-4px" />
        上传剪贴板截图
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ClipboardImageReadError, readClipboardImageFiles } from '@/utils/clipboardImage'

const props = withDefaults(defineProps<{ disabled?: boolean; canPaste?: boolean }>(), { disabled: false, canPaste: true })
const emit = defineEmits<{ files: [files: File[]] }>()
const message = useMessage()
const readingClipboard = ref(false)
const unavailable = computed(() => props.disabled || !props.canPaste)

const uploadClipboardImages = async () => {
  if (unavailable.value || readingClipboard.value) return
  readingClipboard.value = true
  try {
    const files = await readClipboardImageFiles()
    if (!files.length) {
      message.warning('剪贴板中没有图片')
      return
    }
    emit('files', files)
  } catch (cause) {
    message.error(cause instanceof ClipboardImageReadError ? cause.message : '当前浏览器无法读取剪贴板，请使用文件上传')
  } finally {
    readingClipboard.value = false
  }
}
</script>

<style scoped>
.clipboard-upload-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

</style>
