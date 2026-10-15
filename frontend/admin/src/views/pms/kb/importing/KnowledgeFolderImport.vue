<template>
  <el-dialog
    v-model="visible"
    title="文件夹导入"
    width="min(900px, 95vw)"
    :close-on-click-modal="false"
  >
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-alert
      v-if="!supported"
      title="当前浏览器不支持选择文件夹，请使用桌面版 Chrome 或 Edge"
      type="warning"
      :closable="false"
    />
    <p>{{ targetLabel }}</p>
    <p>保留目录层级；同名目录合并，同名文件跳过。空文件夹无法读取。文件保持原格式。</p>
    <el-button
      :disabled="busy || running || !supported || !policy || task?.status === 'FINISHED'"
      v-hasPermi="['pms:kb:library:update']"
      @click="picker?.click()"
    >
      {{ task ? '重新选择原文件夹' : '选择文件夹' }}
    </el-button>
    <input ref="picker" type="file" webkitdirectory multiple hidden @change="selectFolder" />
    <el-button v-if="!policy" :loading="busy" @click="loadPolicy">重新加载上传限制</el-button>
    <p v-if="policy"
      >最多 {{ policy.maxFiles }} 个文件 / {{ size(policy.maxTotalSize) }}，单文件
      {{ size(policy.maxFileSize) }}</p
    >
    <p v-if="busy">{{ checking || '正在处理，请稍候…' }}</p>
    <el-form v-if="mode === 'NEW' && !task" label-width="100px" :disabled="busy" class="mt-16px">
      <el-form-item label="知识库名称" required
        ><el-input v-model="library.name" maxlength="50"
      /></el-form-item>
      <el-form-item label="知识库简介"
        ><el-input v-model="library.description" maxlength="300" type="textarea"
      /></el-form-item>
      <el-form-item label="知识库封面"
        ><UploadImg v-model="library.coverUrl" :limit="1"
      /></el-form-item>
      <el-form-item label="可见范围"
        ><el-radio-group v-model="library.openStatus"
          ><el-radio :value="false">私有</el-radio
          ><el-radio :value="true">公开</el-radio></el-radio-group
        ></el-form-item
      >
      <el-form-item label="初始管理员"
        ><UserSelectV2 v-model="library.adminUserIds" multiple :disabled-ids="[currentUserId]"
      /></el-form-item>
      <el-form-item label="普通成员"
        ><UserSelectV2 v-model="library.memberUserIds" multiple :disabled-ids="[currentUserId]"
      /></el-form-item>
    </el-form>
    <p v-if="entries.length"
      >{{ rootName }}：{{ entries.length }} 个文件，共
      {{ size(entries.reduce((n, f) => n + f.fileSize, 0)) }}</p
    >
    <template v-if="task">
      <p
        >成功 {{ task.success }} / 跳过 {{ task.skipped }} / 失败 {{ task.failed }} / 共
        {{ task.total }}，{{ taskLabel(task.status) }}</p
      >
      <el-progress
        :percentage="Math.round(((task.success + task.skipped) * 100) / (task.total || 1))"
      />
      <div v-for="(progress, path) in active" :key="path" class="upload-row"
        >{{ path }}：{{ progress }}%{{ progress === 100 ? '，正在登记' : '' }}</div
      >
    </template>
    <el-table
      v-if="rows.length"
      :data="rows.slice((rowPage - 1) * 10, rowPage * 10)"
      max-height="340"
      class="mt-12px"
    >
      <el-table-column
        prop="relativePath"
        label="目录 / 文件"
        min-width="230"
        show-overflow-tooltip
      />
      <el-table-column label="状态" width="110"
        ><template #default="{ row }">{{ itemLabel(row.status) }}</template></el-table-column
      >
      <el-table-column prop="reason" label="说明" min-width="160" show-overflow-tooltip />
    </el-table>
    <el-pagination
      v-if="rows.length"
      v-model:current-page="rowPage"
      :page-size="10"
      :total="rows.length"
      layout="prev, pager, next"
    />
    <template #footer>
      <div class="import-actions">
        <el-button
          v-if="!task"
          :disabled="!entries.length || busy"
          v-hasPermi="['pms:kb:library:update']"
          @click="preview"
          >检查导入清单</el-button
        >
        <el-button
          v-if="!task"
          :disabled="!canSubmit || busy"
          type="primary"
          v-hasPermi="['pms:kb:library:update']"
          @click="start"
          >{{ mode === 'NEW' ? '创建并导入' : '开始导入' }}</el-button
        >
        <el-button
          v-if="task && task.status === 'OPEN' && !running"
          :disabled="busy || !entries.length"
          type="primary"
          v-hasPermi="['pms:kb:library:update']"
          @click="continueTask"
          >继续 / 重试失败文件</el-button
        >
        <el-button v-if="running" @click="paused = true">暂停</el-button>
        <el-button
          v-if="task && task.status === 'OPEN'"
          :disabled="running || busy"
          v-hasPermi="['pms:kb:library:update']"
          @click="endTask"
          >结束任务</el-button
        >
        <el-button v-if="task" @click="viewLibrary">查看知识库</el-button>
        <el-button @click="visible = false">收起</el-button>
      </div>
    </template>
  </el-dialog>
  <el-dialog v-model="recordsVisible" title="我的导入记录" width="min(820px, 95vw)">
    <el-alert v-if="recordsError" :title="recordsError" type="error" :closable="false" />
    <el-button :loading="recordsLoading" @click="loadRecords">刷新 / 重试</el-button>
    <el-table v-loading="recordsLoading" :data="records" empty-text="暂无导入记录">
      <el-table-column prop="rootName" label="文件夹" min-width="150" />
      <el-table-column label="结果" min-width="180"
        ><template #default="{ row }"
          >{{ taskLabel(row.status) }}：成功 {{ row.success }}，跳过 {{ row.skipped }}，失败
          {{ row.failed }}</template
        ></el-table-column
      >
      <el-table-column label="操作" width="110"
        ><template #default="{ row }"
          ><el-button link type="primary" :disabled="running" @click="openTask(row.id)"
            >查看 / 继续</el-button
          ></template
        ></el-table-column
      >
    </el-table>
    <el-pagination
      v-model:current-page="recordPage"
      :page-size="10"
      :total="recordTotal"
      layout="prev, pager, next"
      @current-change="loadRecords"
    />
  </el-dialog>
</template>
<script setup lang="ts">
import { ref, reactive, computed, onBeforeUnmount } from 'vue'
import { useRouter, onBeforeRouteLeave } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import CryptoJS from 'crypto-js'
import UserSelectV2 from '@/views/system/user/components/UserSelectV2.vue'
import { getCurrentUserId } from '@/utils/auth'
import type { PmsKnowledgeLibraryVO } from '@/api/pms/kb/library'
import * as api from '@/api/pms/kb/importing'
import { manifest, transient } from './files'
const emit = defineEmits(['success'])
const router = useRouter()
const currentUserId = getCurrentUserId()
const visible = ref(false),
  recordsVisible = ref(false),
  busy = ref(false),
  running = ref(false),
  paused = ref(false)
const policy = ref<api.ImportPolicy>(),
  task = ref<api.ImportTask>()
const mode = ref<'NEW' | 'EXISTING'>('NEW'),
  rootName = ref(''),
  targetLabel = ref(''),
  checking = ref(''),
  error = ref('')
const target = ref<{ libraryId: number; folderId: number }>()
const library = ref<PmsKnowledgeLibraryVO>(defaults())
const picker = ref<HTMLInputElement>(),
  entries = ref<api.ImportEntry[]>([]),
  previewRows = ref<api.ImportItem[]>([])
let files = new Map<string, File>(),
  key = ''
const prepared = ref('')
const active = reactive<Record<string, number>>({})
const rowPage = ref(1),
  recordPage = ref(1),
  recordTotal = ref(0),
  recordsLoading = ref(false),
  recordsError = ref('')
const records = ref<api.ImportTask[]>([])
const supported = 'webkitdirectory' in document.createElement('input')
const rows = computed(() => task.value?.items || previewRows.value)
const canSubmit = computed(
  () =>
    prepared.value === JSON.stringify(request()) &&
    previewRows.value.some((i) => i.status === 'PENDING')
)
function defaults(): PmsKnowledgeLibraryVO {
  return {
    id: undefined as unknown as number,
    name: '',
    description: '',
    openStatus: false,
    adminUserIds: [],
    memberUserIds: []
  }
}
const size = (n: number) =>
  n < 1024
    ? `${n} B`
    : n < 1024 ** 2
      ? `${(n / 1024).toFixed(1)} KB`
      : n >= 1024 ** 3
        ? `${(n / 1024 ** 3).toFixed(2)} GB`
        : `${(n / 1024 ** 2).toFixed(2)} MB`
const itemLabel = (s: string) =>
  ({ PENDING: '待上传', UPLOADING: '处理中', SUCCESS: '成功', SKIPPED: '跳过', FAILED: '失败' })[
    s
  ] || s
const taskLabel = (s: string) =>
  ({ OPEN: '未完成', FINISHED: '已结束', COMPLETED: '已完成' })[s] || s
function message(e: unknown) {
  return e instanceof Error ? e.message : String(e || '请求失败，请重试')
}
async function loadPolicy() {
  policy.value = undefined
  busy.value = true
  try {
    policy.value = await api.getPolicy()
    error.value = ''
  } catch (e) {
    error.value = message(e)
  } finally {
    busy.value = false
  }
}
async function open(destination?: { libraryId: number; folderId: number; label: string }) {
  visible.value = true
  if (running.value) return
  task.value = undefined
  entries.value = []
  previewRows.value = []
  files.clear()
  prepared.value = ''
  key = CryptoJS.lib.WordArray.random(16).toString()
  library.value = defaults()
  mode.value = destination ? 'EXISTING' : 'NEW'
  target.value = destination
  targetLabel.value = destination
    ? `目标：${destination.label} / 所选文件夹`
    : '新建知识库，所选文件夹内部内容直接放入库内根目录'
  rootName.value = ''
  error.value = ''
  rowPage.value = 1
  await loadPolicy()
}
function request(): api.ImportRequest {
  return {
    mode: mode.value,
    rootName: rootName.value,
    requestKey: key,
    files: entries.value,
    ...(mode.value === 'NEW'
      ? { library: library.value }
      : target.value && { libraryId: target.value.libraryId, folderId: target.value.folderId })
  }
}
async function selectFolder(event: Event) {
  const input = event.target as HTMLInputElement
  const selected = Array.from(input.files || [])
  input.value = ''
  if (!selected.length || !policy.value) return
  busy.value = true
  error.value = ''
  prepared.value = ''
  previewRows.value = []
  try {
    const data = await manifest(selected, policy.value, (name) => {
      checking.value = `正在检查：${name}`
    })
    entries.value = data
    files = new Map(selected.map((f) => [f.webkitRelativePath, f]))
    rootName.value = data[0].relativePath.split('/')[0]
    if (!library.value.name) library.value.name = rootName.value.slice(0, 50)
    if (task.value) task.value = await api.resume(task.value.id, data)
  } catch (e) {
    entries.value = []
    files.clear()
    error.value = message(e)
  } finally {
    busy.value = false
    checking.value = ''
  }
}
async function preview() {
  busy.value = true
  error.value = ''
  try {
    const req = request()
    previewRows.value = await api.prepare(req)
    prepared.value = JSON.stringify(req)
  } catch (e) {
    error.value = message(e)
  } finally {
    busy.value = false
  }
}
async function start() {
  if (!canSubmit.value) return
  busy.value = true
  error.value = ''
  try {
    task.value = await api.create(request())
    emit('success')
  } catch (e) {
    error.value = message(e)
  } finally {
    busy.value = false
  }
  if (task.value) await run()
}
async function continueTask() {
  if (!task.value) return
  busy.value = true
  error.value = ''
  try {
    task.value = await api.resume(task.value.id, entries.value)
  } catch (e) {
    error.value = message(e)
    return
  } finally {
    busy.value = false
  }
  await run()
}
async function run() {
  if (!task.value || running.value) return
  const id = task.value.id
  const queue = task.value.items.filter((i) => i.status === 'PENDING' && files.has(i.relativePath))
  running.value = true
  paused.value = false
  async function worker() {
    while (!paused.value && queue.length) {
      const item = queue.shift()!
      active[item.relativePath] = 0
      for (let attempt = 0; attempt < 3; attempt++) {
        try {
          const result = await api.upload(id, item.id, files.get(item.relativePath)!, (n) => {
            active[item.relativePath] = n
          })
          const updated = result.items.find((i) => i.id === item.id)
          if (updated) Object.assign(item, updated)
          break
        } catch (e) {
          if (transient(e) && attempt < 2) {
            await new Promise((resolve) => setTimeout(resolve, 1000 * (attempt + 1)))
            continue
          }
          error.value = message(e)
          item.status = 'FAILED'
          item.reason = message(e)
          // Permission and target failures must stop scheduling; stable error messages come from the service.
          const code = Number((e as { code?: number }).code)
          if (
            (code >= 1000000000 && ![1045090000, 1045090003, 1045090004].includes(code)) ||
            /权限|无权|不可写|不存在|登录|已结束/.test(message(e))
          )
            paused.value = true
          break
        }
      }
      delete active[item.relativePath]
      if (task.value) {
        task.value.success = task.value.items.filter((i) => i.status === 'SUCCESS').length
        task.value.skipped = task.value.items.filter((i) => i.status === 'SKIPPED').length
        task.value.failed = task.value.items.filter((i) => i.status === 'FAILED').length
      }
    }
  }
  try {
    await Promise.all(Array.from({ length: Math.min(policy.value?.concurrency || 3, 3) }, worker))
    task.value = await api.getTask(id)
  } catch (e) {
    error.value = message(e)
  } finally {
    running.value = false
    emit('success')
  }
}
async function endTask() {
  if (!task.value) return
  try {
    await ElMessageBox.confirm('结束后保留已导入内容，未完成文件将不再继续。', '结束导入')
    task.value = await api.finish(task.value.id)
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') error.value = message(e)
  }
}
async function loadRecords() {
  recordsLoading.value = true
  recordsError.value = ''
  try {
    const result = await api.page(recordPage.value)
    records.value = result.list
    recordTotal.value = result.total
  } catch (e) {
    recordsError.value = message(e)
  } finally {
    recordsLoading.value = false
  }
}
async function history() {
  recordsVisible.value = true
  await loadRecords()
}
async function openTask(id: number) {
  try {
    if (task.value?.id !== id) {
      entries.value = []
      files.clear()
    }
    task.value = await api.getTask(id)
    mode.value = task.value.mode
    rootName.value = task.value.rootName
    targetLabel.value = `导入文件夹：${rootName.value}；继续时请选择原文件夹`
    visible.value = true
    recordsVisible.value = false
    rowPage.value = 1
    await loadPolicy()
  } catch (e) {
    recordsError.value = message(e)
  }
}
function viewLibrary() {
  if (task.value) router.push(`/pms/kb/library/${task.value.libraryId}`)
}
function unload(event: BeforeUnloadEvent) {
  if (running.value) {
    event.preventDefault()
    event.returnValue = ''
  }
}
window.addEventListener('beforeunload', unload)
onBeforeRouteLeave(async () => {
  if (!running.value) return true
  try {
    await ElMessageBox.confirm('离开页面将中断上传，下次可重新选择原文件夹继续。', '上传未完成')
    paused.value = true
    return true
  } catch {
    return false
  }
})
onBeforeUnmount(() => {
  paused.value = true
  window.removeEventListener('beforeunload', unload)
})
defineExpose({ open, history })
</script>
<style scoped>
.import-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}
.import-actions :deep(.el-button) {
  margin-left: 0;
}
.upload-row {
  overflow-wrap: anywhere;
  margin: 8px 0;
}
</style>
