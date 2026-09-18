<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getPositioningMaterial, type PositioningMaterial } from '@/api/positioning'
import ConfirmationFile from './ConfirmationFile.vue'
const props = defineProps<{ token: string; id: number; title: string }>()
const data = ref<PositioningMaterial>(), error = ref(''), open = ref(false)
const refresh = async () => {
  error.value = ''
  try { data.value = await getPositioningMaterial(props.token, props.id) }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '历史参考内容不可读取' }
}
const display = (value: unknown): string => {
  if (value == null || value === '') return '未填写'
  if (Array.isArray(value)) return value.map(display).join('、')
  if (typeof value === 'object') {
    const item = value as Record<string, unknown>
    if (item.labelSnapshot || item.label || item.name) return String(item.labelSnapshot || item.label || item.name)
    return Object.values(item).map(display).join('；')
  }
  return String(value)
}
onMounted(refresh)
</script>
<template>
  <div>
    <p>{{ title }}</p>
    <van-image v-if="data?.files.find(f=>f.fieldKey==='__cover__')?.previewUrl" width="100" height="100" fit="contain" :src="data.files.find(f=>f.fieldKey==='__cover__')?.previewUrl" @click="open=true" />
    <p v-if="error">{{ error }} <van-button size="mini" @click="refresh">重试</van-button></p>
    <van-button size="small" @click="open=true">预览参考内容</van-button>
    <van-popup v-model:show="open" position="bottom" round :style="{height:'85dvh',display:'flex',flexDirection:'column'}">
      <van-nav-bar :title="title" right-text="关闭" @click-right="open=false" />
      <div class="material-content">
        <p v-if="error">{{ error }}</p><van-loading v-else-if="!data" />
        <template v-else>
          <van-cell v-for="field in data.fields" :key="field.key" :title="field.label" :label="display(data.dictSnapshot?.[field.key] ?? data.values?.[field.key])" />
          <ConfirmationFile v-for="file in data.files" :key="file.fileId" :name="file.name" :load="async()=>{ const latest=await getPositioningMaterial(token,id); const current=latest.files.find(f=>f.fileId===file.fileId); if(!current)throw new Error('附件已不可读取'); return {id:current.fileId,name:current.name,type:current.contentType,size:current.size,url:current.previewUrl} }" />
        </template>
        <van-button size="small" @click="refresh">刷新预览</van-button>
      </div>
    </van-popup>
  </div>
</template>
<style scoped>
.material-content { overflow-y:auto; min-height:0; padding:16px; white-space:pre-wrap; overflow-wrap:anywhere }
</style>
