<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { checkPermi } from '@/utils/permission'
import StudentInfoQrCard from './StudentInfoQrCard.vue'
import ResourceLinkInput from '@/components/ResourceLinkInput/index.vue'
import * as Api from '@/api/zsjos/registration/studentInfo'
const props = defineProps<{
  caseId: number
  version: number
  status: string
  sent?: boolean
  disabled?: boolean
}>()
const emit = defineEmits<{ changed: []; busy: [boolean] }>()
const data = ref<Api.CollectionLink | null>(null),
  error = ref(''),
  busy = ref(false),
  open = ref(false),
  details = ref<Api.CollectionDetail>(),
  urls = ref<Record<number, string>>({})
let generation = 0
const load = async () => {
  if (!checkPermi(['zsjos:student-info-form:link-read'])) return
  const current = ++generation
  try {
    const value = await Api.link(props.caseId)
    if (current === generation) data.value = value
  } catch (e) {
    if (current === generation) error.value = e instanceof Error ? e.message : '加载失败'
  }
}
watch(
  () => [props.caseId, props.version],
  () => {
    data.value = null
    void load()
  },
  { immediate: true }
)
const mutate = async (operation: string, sent?: boolean) => {
  busy.value = true
  emit('busy', true)
  error.value = ''
  try {
    data.value = await Api.command(props.caseId, operation, {
      version: props.version,
      formId: data.value?.formId,
      sent,
      idempotencyKey: crypto.randomUUID()
    })
    emit('changed')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '操作失败'
  } finally {
    busy.value = false
    emit('busy', false)
  }
}
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
const copy = async () => {
  try {
    if (data.value?.url) await navigator.clipboard.writeText(data.value.url)
  } catch {
    error.value = '复制失败，请手动复制'
  }
}
onBeforeUnmount(() => {
  generation++
  Object.values(urls.value).forEach(URL.revokeObjectURL)
})
</script>
<template>
  <section class="mt-16px"
    ><h3>发送学员信息收集表</h3><p>生成链接并发送后勾选已发送，无需等待学员提交。</p
    ><el-alert v-if="error" :title="error" type="error" :closable="false"
      ><el-button @click="load">重试</el-button></el-alert
    >
    <template v-if="!data"
      ><el-button
        v-hasPermi="['zsjos:student-info-form:create']"
        :disabled="disabled || busy || !['pending', 'processing'].includes(status)"
        @click="mutate('')"
        >生成收集表链接</el-button
      ></template
    >
    <template v-else
      ><el-tag>{{
        data.status === 'SUBMITTED'
          ? '学员已提交'
          : data.status === 'DRAFT'
            ? '待填写'
            : '链接已失效'
      }}</el-tag
      ><div v-if="data.url" class="collection-link"
        ><StudentInfoQrCard :url="data.url" @error="error = $event" /><ResourceLinkInput
          :model-value="data.url"
          readonly
          :clearable="false"
          aria-label="学员报名信息收集表链接"
          ><template #append
            ><el-button @click="copy">复制链接</el-button></template
          ></ResourceLinkInput
        ></div
      ><el-popconfirm
        v-if="data.canRegenerate"
        title="旧链接将失效，需要重新发送，确认？"
        @confirm="mutate('regenerate')"
        ><template #reference
          ><el-button :disabled="busy || disabled">重新生成</el-button></template
        ></el-popconfirm
      ><el-popconfirm v-if="data.canRevoke" title="确认撤销？" @confirm="mutate('revoke')"
        ><template #reference
          ><el-button :disabled="busy || disabled">撤销</el-button></template
        ></el-popconfirm
      ></template
    >
    <div class="mt-12px"
      ><el-checkbox
        :model-value="!!sent"
        :disabled="
          disabled ||
          busy ||
          !data ||
          !['pending', 'processing'].includes(status) ||
          !checkPermi(['zsjos:student-info-form:sent'])
        "
        @change="(value) => mutate('sent', !!value)"
        >我已将收集表链接发送给该学员</el-checkbox
      ></div
    ><p v-if="data?.sentAt">发送确认时间：{{ data.sentAt }}</p
    ><el-button v-hasPermi="['zsjos:student-info-form:read']" @click="view()">查看资料</el-button>
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
