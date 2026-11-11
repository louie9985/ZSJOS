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
  root.querySelectorAll('p').forEach(paragraph => {
    const images = Array.from(paragraph.querySelectorAll('img'))
    const widths = images.map(image => image.style.width || image.getAttribute('width') || '')
    if (images.length < 2 || widths.some(width => !/^\d+(?:\.\d+)?(?:px)?$/.test(width))) return
    const pixels = widths.map(Number.parseFloat)
    if (pixels.some(width => width <= 0) || pixels.reduce((sum, width) => sum + width, 0) > 1352) return
    images.forEach((image, index) => { image.style.width = `${pixels[index] / 1352 * 100}%`; image.removeAttribute('width') })
  })
  // Adapt only the sanitized reading DOM. Never write these widths into the draft.
  root.querySelectorAll<HTMLTableElement>('table').forEach(table => {
    const pixels = (node: HTMLElement) => {
      const value = node.style.width || node.getAttribute('width') || ''
      return /^\d+(?:\.\d+)?(?:px)?$/.test(value) ? Number.parseFloat(value) : 0
    }
    const columns = Array.from(table.querySelectorAll<HTMLTableColElement>(':scope > colgroup > col, :scope > col'))
    let total = pixels(table)
    if (columns.length) total = Math.max(total, columns.reduce((sum, col) => sum + (pixels(col) || 80) * col.span, 0))
    else Array.from(table.rows).forEach(row => { total = Math.max(total, Array.from(row.cells).reduce((sum, cell) => sum + (pixels(cell) || 80 * cell.colSpan), 0)) })
    table.querySelectorAll<HTMLElement>('col,td,th').forEach(node => {
      if (node.closest('table') !== table) return
      const width = pixels(node)
      node.style.removeProperty('min-width')
      if (width && total) { node.style.width = `${width / total * 100}%`; node.removeAttribute('width') }
    })
    table.style.removeProperty('min-width')
    table.removeAttribute('width')
    table.style.width = '100%'
    table.style.tableLayout = 'fixed'
  })
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
.notice-image-content { width: 100%; max-width: 1352px; margin-inline: auto; font: 16px/1.5 system-ui, sans-serif; overflow-wrap: anywhere; }
.notice-image-content :deep(p) { margin: 15px 0; }
.notice-image-content :deep(img), .notice-image-content :deep(video) { max-width: 100%; height: auto; }
.notice-image-content :deep(table) { border-collapse: collapse; }
.notice-image-content :deep(td), .notice-image-content :deep(th) { box-sizing: border-box; border: 1px solid var(--el-border-color); padding: 8px; line-height: 1.5; white-space: pre-wrap; overflow-wrap: anywhere; vertical-align: top; }
</style>
