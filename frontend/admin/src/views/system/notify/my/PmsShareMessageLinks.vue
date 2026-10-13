<template>
  <el-space v-if="targets" direction="vertical" alignment="start">
    <el-link :href="targets.publicUrl" target="_blank" rel="noopener noreferrer" type="primary">公开查看</el-link>
    <el-link :href="router.resolve(targets.documentRoute).href" type="primary" @click.prevent="router.push(targets.documentRoute)">在知识库中查看</el-link>
  </el-space>
</template>
<script setup lang="ts">
const props = defineProps<{ params?: Record<string, unknown> }>()
const router = useRouter()
const targets = computed(() => {
  const { publicUrl, documentRoute } = props.params || {}
  if (typeof publicUrl !== 'string' || typeof documentRoute !== 'string'
    || !/^\/pms\/kb\/library\/\d+\/document\/\d+$/.test(documentRoute)
    || /[\u0000-\u0020\\]/.test(publicUrl)) return undefined
  try {
    const url = new URL(publicUrl)
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) return undefined
    return { publicUrl: url.href, documentRoute }
  } catch { return undefined }
})
</script>
