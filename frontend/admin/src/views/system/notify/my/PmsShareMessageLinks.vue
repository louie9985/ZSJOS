<template>
  <el-link v-if="targets" :href="targets.publicUrl" target="_blank" rel="noopener noreferrer" type="primary">公开查看</el-link>
</template>
<script setup lang="ts">
const props = defineProps<{ params?: Record<string, unknown> }>()
const targets = computed(() => {
  const { publicUrl } = props.params || {}
  if (typeof publicUrl !== 'string'
    || /[\u0000-\u0020\\]/.test(publicUrl)) return undefined
  try {
    const url = new URL(publicUrl)
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) return undefined
    return { publicUrl: url.href }
  } catch { return undefined }
})
</script>
