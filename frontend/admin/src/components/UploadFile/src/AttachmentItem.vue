<template>
  <div class="attachment-item">
    <el-image
      v-if="image && !imageFailed"
      :src="imageSrc"
      :preview-src-list="[imageSrc]"
      preview-teleported
      fit="cover"
      class="attachment-thumbnail"
      @error="imageFailed = true"
    />
    <el-button v-else-if="image" link type="danger" @click="retryImage"
      >图片加载失败，点击重试</el-button
    >
    <span class="attachment-name">{{ displayName }}</span>
    <el-button v-if="previewable && !imageFailed" link type="primary" @click="previewVisible = true"
      >预览</el-button
    >
    <el-link
      :href="imageSrc"
      target="_blank"
      rel="noopener noreferrer"
      download
      :underline="false"
      type="primary"
      >下载</el-link
    >
    <Dialog v-model="previewVisible" :title="displayName" width="min(900px, 94vw)">
      <FilePreview v-if="previewVisible" :url="imageSrc" :file-name="displayName" downloadable />
    </Dialog>
  </div>
</template>
<script setup lang="ts">
import { FilePreview } from '@/components/FilePreview'
import { getFileNameFromUrl, getFileExtension } from '@/utils/file'
import { retryImageUrl } from '@/utils/imageRetry'

const props = defineProps<{ url: string; name?: string }>()
const previewVisible = ref(false)
const imageFailed = ref(false)
const retryAttempt = ref(0)
const imageSrc = ref(props.url)
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
    retryAttempt.value = 0
    imageSrc.value = props.url
    previewVisible.value = false
  }
)
function retryImage() {
  retryAttempt.value += 1
  imageFailed.value = false
  imageSrc.value = retryImageUrl(props.url, retryAttempt.value)
}
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
