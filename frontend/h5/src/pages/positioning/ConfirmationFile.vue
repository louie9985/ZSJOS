<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { showImagePreview } from 'vant'
import type { PositioningFile } from '@/api/positioning'
const props = defineProps<{ name: string; load: () => Promise<PositioningFile> }>()
const file = ref<PositioningFile>(), error = ref(''), loading = ref(false)
const refresh = async () => {
  loading.value = true; error.value = ''
  try { const value = await props.load(); if (!value.url || !/^https?:$/.test(new URL(value.url).protocol)) throw new Error('附件地址不可用'); file.value = value }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '附件读取失败' }
  finally { loading.value = false }
}
onMounted(refresh)
</script>
<template>
  <div class="confirmation-file">
    <p>{{ name }}</p><van-loading v-if="loading" size="20" />
    <p v-if="error">{{ error }} <van-button size="mini" @click="refresh">重试</van-button></p>
    <template v-if="file?.url">
      <van-image v-if="file.type?.startsWith('image/')" width="100" height="100" fit="contain" :src="file.url" :alt="name" @click="showImagePreview([file.url])" @error="error='图片加载失败，请重试'" />
      <audio v-else-if="file.type?.startsWith('audio/')" :src="file.url" controls />
      <video v-else-if="file.type?.startsWith('video/')" :src="file.url" controls />
      <p><a :href="file.url" target="_blank" rel="noopener noreferrer" :download="name">下载附件</a> · <a href="#" @click.prevent="refresh">刷新地址</a></p>
    </template>
  </div>
</template>
<style scoped>
.confirmation-file { overflow-wrap:anywhere; padding:8px 0 }
audio, video { max-width:100%; max-height:280px }
</style>
