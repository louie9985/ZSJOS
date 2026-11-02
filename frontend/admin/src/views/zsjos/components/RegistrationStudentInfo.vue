<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { checkPermi } from '@/utils/permission'
import * as Api from '@/api/zsjos/registration/studentInfo'
const props = defineProps<{
  caseId: number
  version: number
  status: string
  sent?: boolean
  disabled?: boolean
}>()
defineEmits<{ changed: []; busy: [boolean] }>()
const summary = ref<Api.CollectionDetail | null>(null),
  error = ref(''),
  busy = ref(false),
  open = ref(false),
  details = ref<Api.CollectionDetail>(),
  urls = ref<Record<number, string>>({})
let generation = 0
const load = async () => {
  if (!checkPermi(['zsjos:student-info-form:read'])) return
  const current = ++generation
  try {
    const value = await Api.detail(props.caseId)
    if (current === generation) summary.value = value
  } catch (e) {
    if (current === generation) error.value = e instanceof Error ? e.message : '加载失败'
  }
}
watch(
  () => [props.caseId, props.version],
  () => {
    summary.value = null
    void load()
  },
  { immediate: true }
)
const view = async (sensitive = false) => {
  busy.value = true
  try {
    details.value = await Api.detail(props.caseId, sensitive)
    open.value = true
  } catch (e) {
    error.value = e instanceof Error ? e.message : '资料加载失败'
  } finally {
    busy.value = false
  }
}
const viewImages = async () => {
  if (!details.value) return
  try {
    for (const i of details.value.images)
      if (!urls.value[i.id]) urls.value[i.id] = await Api.image(props.caseId, i)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '图片加载失败'
  }
}
const exportData = async () => {
  try {
    const blob = await Api.download(props.caseId)
    if (blob.type.includes('json')) throw new Error('导出失败')
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '学员信息.xlsx'
    a.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '导出失败'
  }
}
onBeforeUnmount(() => {
  generation++
  Object.values(urls.value).forEach(URL.revokeObjectURL)
})
</script>
<template>
  <section class="mt-16px">
    <h3>报名资料收集</h3>
    <p>资料由员工工作台的“报名资料收集”统一处理，此处同步展示资料状态。</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button @click="load">重试</el-button></el-alert>
    <el-tag>{{ summary?.status === 'SUBMITTED' ? '资料已完成' : summary?.status === 'DRAFT' ? '待填写' : '尚无已提交资料' }}</el-tag>
    <el-tag v-if="sent">已确认发送</el-tag>
    <el-button v-hasPermi="['zsjos:student-info-form:read']" @click="view()">查看资料</el-button>
    <el-dialog v-model="open" title="学员报名资料" width="min(800px,95vw)" append-to-body
      ><template v-if="details"
        ><el-button v-if="details.canReadSensitive" @click="view(true)">查看完整敏感信息</el-button
        ><el-button v-if="details.canReadSensitive && details.images.length" @click="viewImages"
          >查看材料附件</el-button
        ><el-button v-if="details.canExport" @click="exportData">导出</el-button
        ><el-empty
          v-if="details.status !== 'SUBMITTED'"
          description="尚未提交资料"
        /><el-descriptions v-else :column="2" border
          ><el-descriptions-item v-for="f in details.fields" :key="f.key" :label="f.label"
            ><template v-if="f.type === 'images'"
              ><template v-for="i in details.images.filter((x) => x.fieldKey === f.key)" :key="i.id"
                ><el-image
                  v-if="urls[i.id] && i.contentType.startsWith('image/')"
                  :src="urls[i.id]"
                  :preview-src-list="Object.values(urls)"
                  fit="cover"
                  style="width: 80px; height: 80px"
                /><a v-else-if="urls[i.id]" :href="urls[i.id]" :download="i.name">{{ i.name }}</a><span v-else>{{ i.name }} </span></template
              ></template
            ><template v-else>{{ details.values[f.key] || '—' }}</template></el-descriptions-item
          ></el-descriptions
        ></template
      ></el-dialog
    >
  </section>
</template>
<style scoped>
.collection-link {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  margin: 12px 0;
}
.collection-link :deep(.resource-link-input) {
  flex-basis: 100%;
}
</style>
