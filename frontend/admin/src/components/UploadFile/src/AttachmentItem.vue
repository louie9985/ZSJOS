<template>
  <div class="attachment-item">
    <el-image
      v-if="image && !imageFailed"
      :src="url"
      :preview-src-list="[url]"
      preview-teleported
      fit="cover"
      class="attachment-thumbnail"
      @error="imageFailed = true"
    />
    <span class="attachment-name">{{ displayName }}</span>
    <el-button v-if="previewable && !imageFailed" link type="primary" @click="previewVisible = true"
      >预览</el-button
    >
    <el-link
      :href="url"
      target="_blank"
      rel="noopener noreferrer"
      download
      :underline="false"
      type="primary"
      >下载</el-link
    >
    <Dialog v-model="previewVisible" :title="displayName" width="min(900px, 94vw)">
      <FilePreview v-if="previewVisible" :url="url" :file-name="displayName" downloadable />
    </Dialog>
  </div>
</template>
<script setup lang="ts">
import { FilePreview } from '@/components/FilePreview'
import { getFileNameFromUrl, getFileExtension } from '@/utils/file'

const props = defineProps<{ url: string; name?: string }>()
const previewVisible = ref(false)
const imageFailed = ref(false)
const displayName = computed(() => props.name || getFileNameFromUrl(props.url))
const extension = computed(() => getFileExtension(displayName.value))
const image = computed(() =>
  ['bmp', 'gif', 'jpeg', 'jpg', 'png', 'svg', 'webp'].includes(extension.value)
)
const previewable = computed(
  () =>
    image.value ||
    ['pdf', 'txt', 'm4v', 'mov', 'mp4', 'ogg', 'webm', 'aac', 'flac', 'm4a', 'mp3', 'wav'].includes(
      extension.value
    )
)
watch(
  () => props.url,
  () => {
    imageFailed.value = false
    previewVisible.value = false
  }
)
</script>
<style scoped>
.attachment-item {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
  max-width: 100%;
}
.attachment-thumbnail {
  width: 64px;
  height: 64px;
  flex-shrink: 0;
  border-radius: 4px;
}
.attachment-name {
  overflow-wrap: anywhere;
  min-width: 0;
}
</style>
