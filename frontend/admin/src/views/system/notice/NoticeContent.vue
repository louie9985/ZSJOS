<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElImageViewer } from 'element-plus'

const props = defineProps<{ html: string }>()
const preview = ref<{ urls: string[]; index: number }>()
watch(() => props.html, () => { preview.value = undefined })
const openImage = (event: MouseEvent | KeyboardEvent) => {
  if (!(event.target instanceof HTMLImageElement) || !event.target.getAttribute('src')) return
  if ('key' in event && event.key !== 'Enter' && event.key !== ' ') return
  const images = Array.from((event.currentTarget as HTMLElement).querySelectorAll<HTMLImageElement>('img[src]')).filter(image => image.getAttribute('src'))
  event.preventDefault()
  event.stopPropagation()
  preview.value = { urls: images.map(image => image.currentSrc || image.src), index: images.indexOf(event.target) }
}
// Decorate only the sanitized display DOM, never the persisted editor HTML.
const vPreviewImages = {
  mounted: decorateImages,
  updated: decorateImages
}
function decorateImages(root: HTMLElement) {
  root.querySelectorAll<HTMLImageElement>('img[src]').forEach(image => {
    image.tabIndex = 0
    image.setAttribute('role', 'button')
    image.setAttribute('aria-label', `预览图片：${image.alt || '公告图片'}`)
  })
}
</script>

<template>
  <div>
    <div v-dompurify-html="html" v-preview-images class="notice-image-content" @click="openImage" @keydown="openImage"></div>
    <ElImageViewer v-if="preview" :url-list="preview.urls" :initial-index="preview.index" teleported @close="preview = undefined" />
  </div>
</template>

<style scoped>
.notice-image-content :deep(img[src]) { cursor: zoom-in; }
</style>
