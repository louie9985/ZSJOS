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
const emit = defineEmits<{ changed: []; busy: [boolean] }>()
const summary = ref<Api.CollectionDetail | null>(null),
  error = ref(''),
  busy = ref(false),
  open = ref(false),
  details = ref<Api.CollectionDetail>(),
  urls = ref<Record<number, string>>({})
const task = ref<Api.StudentInfoTask>()
const taskLoading = ref(false)
const taskError = ref('')
const linkOpen = ref(false)
const link = ref<Api.CollectionLink | null>(null)
const generationOpen = ref(false)
const config = ref<Api.StudentInfoGenerationConfig>()
const materialKeys = ref<string[]>([])
const archive = ref<Api.CollectionDetail>()
const archiveOpen = ref(false)
const archiveSource = ref<Api.StudentInfoTask['candidates'][number]>()
const statusLabels: Record<string, string> = {
  PENDING: '待处理', UNSENT: '待发送', DRAFT: '待填写', SUBMITTED: '已提交',
  REUSED: '已复用', EXPIRED: '链接已过期', REVOKED: '链接已撤销', CANCELLED: '已取消'
}
const permitted = (permission: string) => checkPermi([permission])
const canGenerate = () => permitted('zsjos:student-info-form:create') && permitted('zsjos:student-info-form:sensitive-read')
const withTask = async (action: (current: Api.StudentInfoTask) => Promise<void>) => {
  if (!task.value || busy.value || props.disabled) return
  busy.value = true
  emit('busy', true)
  taskError.value = ''
  try {
    await action(task.value)
  } catch (error) {
    taskError.value = error instanceof Error ? error.message : '资料操作失败'
  } finally {
    busy.value = false
    emit('busy', false)
  }
}
const openGeneration = () => withTask(async (current) => {
  config.value = await Api.taskGenerationConfig(current.id)
  materialKeys.value = config.value.defaultSelectedKeys ?? []
  generationOpen.value = true
})
const showLink = async (id: number) => {
  link.value = await Api.taskLink(id)
  linkOpen.value = true
}
const openLink = () => withTask(current => showLink(current.id))
const runCommand = (operation: Parameters<typeof Api.taskCommand>[1]) => withTask(async (current) => {
  const options = operation === 'generate'
    ? { configVersionId: config.value?.configVersionId, materialKeys: materialKeys.value }
    : operation === 'reuse' && archiveSource.value
      ? { sourceFormId: archiveSource.value.formId, sourceRevision: archiveSource.value.revision }
      : undefined
  task.value = await Api.taskCommand(current, operation, options)
  emit('changed')
  if (operation === 'generate') {
    generationOpen.value = false
    if (permitted('zsjos:student-info-form:link-read')) await showLink(current.id)
  }
  if (operation === 'regenerate' || operation === 'revoke') await showLink(current.id)
  if (operation === 'reuse') archiveOpen.value = false
})
const viewArchive = () => withTask(async (current) => {
  const source = current.candidates.find(candidate => candidate.missing.length === 0) ?? current.candidates[0]
  if (!source) return
  archive.value = await Api.taskArchive(current.id, source.formId, source.revision, permitted('zsjos:student-info-form:sensitive-read'))
  archiveSource.value = source
  archiveOpen.value = true
})
let generation = 0
let summaryGeneration = 0
const loadTask = async () => {
  const current = ++generation
  if (!permitted('zsjos:registration:query-my')) return
  taskLoading.value = true
  taskError.value = ''
  try {
    const result = await Api.taskByRegistration(props.caseId)
    if (current === generation) task.value = result
  } catch (error) {
    if (current === generation) taskError.value = error instanceof Error ? error.message : '收集任务加载失败'
  } finally {
    if (current === generation) taskLoading.value = false
  }
}
const load = async () => {
  if (!checkPermi(['zsjos:student-info-form:read'])) return
  const current = ++summaryGeneration
  error.value = ''
  try {
    const value = await Api.detail(props.caseId)
    if (current === summaryGeneration) summary.value = value
  } catch (e) {
    if (current === summaryGeneration) error.value = e instanceof Error ? e.message : '加载失败'
  }
}
watch(
  () => [props.caseId, props.version],
  () => {
    summary.value = null
    void load()
    void loadTask()
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
  summaryGeneration++
  Object.values(urls.value).forEach(URL.revokeObjectURL)
})
</script>
<template>
  <section class="mt-16px">
    <h3>报名资料收集</h3>
    <el-alert v-if="taskError" :title="taskError" type="error" :closable="false">
      <el-button v-if="!task" link @click="loadTask">重试</el-button>
    </el-alert>
    <div class="collection-link" v-loading="taskLoading">
      <el-tag v-if="task">{{ statusLabels[task.status] || '未知状态' }}</el-tag>
      <el-button v-if="task?.canProcess && !task.formId && canGenerate()" :disabled="disabled || busy" @click="openGeneration">生成收集表</el-button>
      <el-button v-if="task?.formId && permitted('zsjos:student-info-form:link-read')" :disabled="disabled || busy" @click="openLink">查看链接</el-button>
      <el-button v-if="task?.canProcess && task.status === 'UNSENT' && permitted('zsjos:student-info-form:sent')" :disabled="disabled || busy" @click="runCommand('sent')">确认已发送</el-button>
      <el-button v-if="task?.candidates.length && permitted('zsjos:student-info-form:read')" :disabled="disabled || busy" @click="viewArchive">查看已有档案</el-button>
    </div>
    <el-dialog v-model="generationOpen" title="生成收集表" width="min(520px, 95vw)" append-to-body :show-close="!busy" :close-on-click-modal="false">
      <el-alert v-if="taskError" :title="taskError" type="error" :closable="false"/>
      <p>学历提升类产品默认勾选三项报考信息。可逐项调整报考信息、材料和邮寄地址。</p>
      <el-checkbox-group v-if="config" v-model="materialKeys" :disabled="busy">
        <el-checkbox v-for="field in config.fields.filter(field => config?.selectableMaterialKeys.includes(field.key))" :key="field.key" :value="field.key">{{ field.label }}</el-checkbox>
      </el-checkbox-group>
      <template #footer><el-button type="primary" :loading="busy" :disabled="!config" @click="runCommand('generate')">生成链接</el-button></template>
    </el-dialog>
    <el-dialog v-model="linkOpen" title="收集表链接" width="min(520px, 95vw)" append-to-body :show-close="!busy">
      <el-alert v-if="taskError" :title="taskError" type="error" :closable="false"/>
      <el-input v-if="link?.url" :model-value="link.url" readonly aria-label="收集表链接" />
      <el-empty v-else description="暂无可用的收集表链接" />
      <template #footer>
        <el-popconfirm v-if="task?.canProcess && link?.canRevoke && permitted('zsjos:student-info-form:revoke')" title="撤销后，当前链接将失效。确认撤销？" @confirm="runCommand('revoke')">
          <template #reference><el-button :disabled="busy">撤销链接</el-button></template>
        </el-popconfirm>
        <el-popconfirm v-if="task?.canProcess && link?.canRegenerate && permitted('zsjos:student-info-form:regenerate')" title="重新生成后，当前链接将失效。确认生成？" @confirm="runCommand('regenerate')">
          <template #reference><el-button :disabled="busy">重新生成</el-button></template>
        </el-popconfirm>
      </template>
    </el-dialog>
    <el-dialog v-model="archiveOpen" title="已有档案" width="min(800px, 95vw)" append-to-body :show-close="!busy">
      <el-alert v-if="taskError" :title="taskError" type="error" :closable="false"/>
      <el-alert v-if="archiveSource?.missing.length" type="warning" :title="'本次项目缺少 ' + archiveSource.missing.join('、')" :closable="false"/>
      <el-descriptions v-if="archive" :column="2" border>
        <el-descriptions-item v-for="field in archive.fields" :key="field.key" :label="field.label">
          {{ field.type === 'images' ? archive.images.filter(image => image.fieldKey === field.key).map(image => image.name).join('、') || '—' : archive.values[field.key] || '—' }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-popconfirm v-if="task?.canProcess && archiveSource && !archiveSource.missing.length && !['SUBMITTED', 'REUSED'].includes(task.status) && permitted('zsjos:student-info-task:reuse') && permitted('zsjos:student-info-form:sensitive-read')" title="复用后，当前未提交的填写链接将失效。确认复用？" @confirm="runCommand('reuse')">
          <template #reference><el-button type="primary" :loading="busy">复用已有档案</el-button></template>
        </el-popconfirm>
      </template>
    </el-dialog>
    <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button @click="load">重试</el-button></el-alert>
    <el-tag v-if="!task">{{ summary?.status === 'SUBMITTED' ? '资料已完成' : summary?.status === 'DRAFT' ? '待填写' : '尚无已提交资料' }}</el-tag>
    <el-tag v-if="sent">已确认发送</el-tag>
    <el-button v-hasPermi="['zsjos:student-info-form:read']" @click="view()">查看资料</el-button>
    <el-dialog v-model="open" title="学员报名资料" width="min(800px,95vw)" append-to-body
      ><template v-if="details"
        ><section v-if="details.agreementVersion">
          <p>已确认协议 · V{{ details.agreementVersion }}</p>
          <details v-if="details.agreementBody"><summary>查看协议全文</summary><article v-dompurify-html="details.agreementBody"></article></details>
        </section><el-button v-if="details.canReadSensitive" @click="view(true)">查看完整敏感信息</el-button
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
