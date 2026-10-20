<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { formatDate } from '@/utils/formatTime'
import * as Api from '@/api/zsjos/studentArchiveExtra'
import type { CollectionImage } from '@/api/zsjos/registration/studentInfo'
const props = defineProps<{ relationId: number }>()
const data = ref<Api.Extra>(),
  error = ref(''),
  busy = ref(false),
  editing = ref(false)
const changes = ref<Record<string, Api.ExtraValue>>({}),
  reason = ref(''),
  attachments = ref<CollectionImage[]>([])
let sequence = 0,
  key = ''
const empty = (): Api.ExtraValue => ({ text: '', links: [], attachmentIds: [] })
const message = (e: unknown) => (e instanceof Error ? e.message : '操作失败，请重试')
const load = async () => {
  const current = ++sequence
  busy.value = true
  error.value = ''
  try {
    const next = await Api.detail(props.relationId)
    if (current === sequence) {
      data.value = next
      attachments.value = next.attachments
    }
  } catch (e) {
    if (current === sequence) {
      data.value = undefined
      error.value = message(e)
    }
  } finally {
    if (current === sequence) busy.value = false
  }
}
watch(
  () => props.relationId,
  () => {
    editing.value = false
    data.value = undefined
    void load()
  },
  { immediate: true }
)
onBeforeUnmount(() => {
  sequence++
})
const value = (field: string) => changes.value[field] || data.value?.values[field] || empty()
const change = (field: string, patch: Partial<Api.ExtraValue>) => {
  changes.value[field] = { ...value(field), ...patch }
  key = crypto.randomUUID()
}
const edit = () => {
  changes.value = {}
  reason.value = ''
  key = crypto.randomUUID()
  editing.value = true
}
const upload = async (f: Api.ExtraField, file: File) => {
  const ext = file.name.split('.').pop()?.toLowerCase() || ''
  if (!f.allowedExtensions.includes(ext) || file.size > 10 * 1024 * 1024) {
    error.value = '请选择符合字段格式要求且不超过 10MB 的附件'
    return false
  }
  busy.value = true
  error.value = ''
  const generation = sequence
  try {
    const item = await Api.upload(props.relationId, f.key, file)
    if (generation === sequence) {
      attachments.value.push(item)
      change(f.key, { attachmentIds: [...value(f.key).attachmentIds, item.id] })
    }
  } catch (e) {
    if (generation === sequence) error.value = message(e)
  } finally {
    if (generation === sequence) busy.value = false
  }
  return false
}
const download = async (item: CollectionImage) => {
  busy.value = true
  error.value = ''
  try {
    await Api.download(props.relationId, item)
  } catch (e) {
    error.value = message(e)
  } finally {
    busy.value = false
  }
}
const save = async () => {
  if (!data.value) return
  busy.value = true
  error.value = ''
  try {
    const payload = Object.fromEntries(
      Object.entries(changes.value).map(([field, v]) => [
        field,
        { ...v, links: v.links.map((s) => s.trim()).filter(Boolean) }
      ])
    )
    await Api.save(props.relationId, data.value.revision, payload, reason.value, key)
    editing.value = false
    await load()
  } catch (e) {
    error.value = message(e)
  } finally {
    busy.value = false
  }
}
const renewKey = () => {
  key = crypto.randomUUID()
}
</script>
<template>
  <section aria-label="考试与证书信息">
    <div class="extra-actions"
      ><strong>考试与证书信息</strong><el-tag>V{{ data?.revision || 0 }}</el-tag>
      <el-button :disabled="busy || editing" @click="load">刷新考试与证书信息</el-button>
      <el-button v-if="data?.canMaintain" :disabled="busy" @click="edit"
        >维护考试与证书信息</el-button
      >
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false"
      ><el-button v-if="!editing" :disabled="busy" @click="load">重试</el-button></el-alert
    >
    <div v-loading="busy">
      <template v-if="data">
        <el-empty
          v-if="!Object.keys(data.values).length"
          description="暂无考试与证书信息，可独立于收集表维护"
        />
        <el-descriptions :column="1" border>
          <el-descriptions-item v-for="f in data.fields" :key="f.key" :label="f.label">
            <div class="extra-content">{{ data.values[f.key]?.text || '—' }}</div>
            <div v-for="(link, i) in data.values[f.key]?.links" :key="i" class="extra-content"
              ><a :href="link" target="_blank" rel="noopener noreferrer">{{ link }}</a></div
            >
            <div
              v-for="a in data.attachments.filter((a) =>
                data?.values[f.key]?.attachmentIds.includes(a.id)
              )"
              :key="a.id"
            >
              <el-button link :disabled="busy" @click="download(a)">{{ a.name }}</el-button>
            </div>
            <span v-if="!data.canReadAttachments && data.values[f.key]?.attachmentIds.length"
              >附件需敏感信息查看权限</span
            >
          </el-descriptions-item>
        </el-descriptions>
        <el-collapse
          ><el-collapse-item title="考试与证书维护记录">
            <p v-for="h in data.history" :key="h.revision"
              >V{{ h.revision }} · {{ formatDate(h.modifiedAt) }} · {{ h.operatorName }} ·
              {{ h.reason }}</p
            >
            <el-empty v-if="!data.history.length" description="暂无维护记录" /> </el-collapse-item
        ></el-collapse>
      </template>
    </div>
    <el-dialog
      :model-value="editing"
      title="维护考试与证书信息"
      width="min(760px,95vw)"
      append-to-body
      :close-on-click-modal="false"
      :show-close="!busy"
      @close="editing = false"
    >
      <el-form label-position="top">
        <el-form-item v-for="f in data?.fields" :key="f.key" :label="f.label">
          <div class="extra-field">
            <el-input
              :model-value="value(f.key).text"
              type="textarea"
              :rows="f.type === 'textarea' ? 3 : 1"
              :maxlength="2000"
              :disabled="
                busy || (f.key === 'certificate_shipping_address' && !data?.canReadAttachments)
              "
              :placeholder="f.type === 'number' ? '填写数字，最多两位小数' : '填写文本'"
              @update:model-value="(text) => change(f.key, { text })"
            />
            <template v-if="f.type === 'composite'">
              <el-input
                :model-value="value(f.key).links.join('\n')"
                type="textarea"
                :rows="2"
                :disabled="busy"
                placeholder="链接（每行一个，可与文本和附件同时保存）"
                @update:model-value="(s) => change(f.key, { links: s.split('\n') })"
              />
              <div v-for="id in value(f.key).attachmentIds" :key="id">
                {{ attachments.find((a) => a.id === id)?.name || '已上传附件' }}
                <el-button
                  :disabled="busy || !data?.canReadAttachments"
                  @click="
                    change(f.key, {
                      attachmentIds: value(f.key).attachmentIds.filter((n) => n !== id)
                    })
                  "
                  >移除</el-button
                >
              </div>
              <el-upload
                :accept="f.allowedExtensions.map((e) => '.' + e).join(',')"
                :show-file-list="false"
                :multiple="false"
                :disabled="
                  busy || !data?.canReadAttachments || value(f.key).attachmentIds.length >= 9
                "
                :before-upload="(file) => upload(f, file)"
                ><el-button
                  :disabled="
                    busy || !data?.canReadAttachments || value(f.key).attachmentIds.length >= 9
                  "
                  >上传附件</el-button
                ></el-upload
              >
              <small>支持 {{ f.allowedExtensions.join('/').toUpperCase() }}，单个不超过 10MB</small>
            </template>
          </div>
        </el-form-item>
        <el-form-item label="维护原因" required
          ><el-input
            v-model="reason"
            type="textarea"
            :maxlength="500"
            :disabled="busy"
            @input="renewKey"
        /></el-form-item>
      </el-form>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <template #footer
        ><el-button :disabled="busy" @click="editing = false">取消</el-button
        ><el-button
          type="primary"
          :loading="busy"
          :disabled="!reason.trim() || !Object.keys(changes).length"
          @click="save"
          >保存档案</el-button
        ></template
      >
    </el-dialog>
  </section>
</template>
<style scoped>
.extra-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}
.extra-content {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.extra-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  min-width: 0;
}
</style>
