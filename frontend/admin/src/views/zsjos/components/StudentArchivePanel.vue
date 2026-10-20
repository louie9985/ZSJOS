<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import StudentArchiveExtraPanel from './StudentArchiveExtraPanel.vue'
import * as Api from '@/api/zsjos/studentArchive'
import { getAreaTree, type AreaVO } from '@/api/system/area'
import type { CollectionImage } from '@/api/zsjos/registration/studentInfo'
const props = defineProps<{ relationId: number }>()
const data = ref<Api.Archive>(),
  editor = ref<Api.Edit>(),
  busy = ref(false),
  error = ref(''),
  reason = ref(''),
  changes = ref<Record<string, unknown>>({}),
  areas = ref<AreaVO[]>([]),
  images = ref<CollectionImage[]>([]),
  urls = ref<Record<number, string>>({})
let sequence = 0,
  key = ''
const message = (e: unknown) => (e instanceof Error ? e.message : '操作失败，请重试')
const load = async (sensitive = false) => {
  const current = ++sequence
  busy.value = true
  error.value = ''
  try {
    const d = await Api.detail(props.relationId, sensitive)
    if (current === sequence) data.value = d
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
    data.value = undefined
    editor.value = undefined
    Object.values(urls.value).forEach(URL.revokeObjectURL)
    urls.value = {}
    void load()
  },
  { immediate: true }
)
onBeforeUnmount(() => {
  sequence++
  Object.values(urls.value).forEach(URL.revokeObjectURL)
})
const edit = async () => {
  busy.value = true
  error.value = ''
  try {
    const [e, a] = await Promise.all([Api.edit(props.relationId), getAreaTree()])
    editor.value = e
    areas.value = a
    changes.value = {}
    images.value = e.images
    reason.value = ''
    key = crypto.randomUUID()
  } catch (e) {
    error.value = message(e)
  } finally {
    busy.value = false
  }
}
const change = (field: string, value: unknown) => {
  changes.value[field] = value
  key = crypto.randomUUID()
}
const download = async () => {
  busy.value = true
  try {
    const blob = await Api.download(props.relationId)
    if (blob.type.includes('json')) throw Error('导出失败')
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '学员档案.xlsx'
    a.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (e) {
    error.value = message(e)
  } finally {
    busy.value = false
  }
}
const renewKey = () => {
  key = crypto.randomUUID()
}
const value = (field: string) =>
  Object.hasOwn(changes.value, field) ? changes.value[field] : editor.value?.values[field]
const remove = (item: CollectionImage) => {
  images.value = images.value.filter((i) => i.id !== item.id)
  change(
    item.fieldKey,
    images.value.filter((i) => i.fieldKey === item.fieldKey).map((i) => i.id)
  )
}
const upload = async (fieldKey: string, side: string, file: File, maxSizeMb: number, allowed = ['jpg', 'jpeg', 'png']) => {
  if (!allowed.includes(file.name.split('.').pop()?.toLowerCase() || '') || file.size > maxSizeMb * 1024 * 1024) {
    error.value = '请选择符合字段格式和大小要求的附件'
    return false
  }
  busy.value = true
  error.value = ''
  try {
    const i = await Api.upload(props.relationId, fieldKey, side, file)
    images.value.push(i)
    change(
      fieldKey,
      images.value.filter((x) => x.fieldKey === fieldKey).map((x) => x.id)
    )
  } catch (e) {
    error.value = message(e)
  } finally {
    busy.value = false
  }
  return false
}
const save = async () => {
  if (!editor.value) return
  busy.value = true
  error.value = ''
  try {
    await Api.save(props.relationId, editor.value.revision, changes.value, reason.value, key)
    editor.value = undefined
    await load()
  } catch (e) {
    error.value = message(e)
  } finally {
    busy.value = false
  }
}
const preview = async () => {
  if (!data.value) return
  busy.value = true
  try {
    const entries = await Promise.all(
      data.value.information.images.map(
        async (i) => [i.id, await Api.image(props.relationId, i)] as const
      )
    )
    Object.values(urls.value).forEach(URL.revokeObjectURL)
    urls.value = Object.fromEntries(entries)
  } catch (e) {
    error.value = message(e)
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <section>
    <StudentArchiveExtraPanel :key="relationId" :relation-id="relationId" />
    <h4>报名收集资料</h4>
    <el-button :loading="busy" @click="load()">刷新</el-button
    ><el-tag>档案版本 V{{ data?.revision || 0 }}</el-tag>
    <el-button v-if="data?.information.canExport" :disabled="busy" @click="download"
      >导出学员档案</el-button
    ><el-button v-if="data?.canMaintain" :disabled="busy" @click="edit">维护学员档案</el-button>
    <template v-if="data?.information.status === 'SUBMITTED' && data.information.canReadSensitive"
      ><el-button :disabled="busy" @click="load(true)">查看完整敏感信息</el-button
      ><el-button :disabled="busy" @click="preview">查看材料附件</el-button></template
    >
    <el-alert v-if="error" :title="error" type="error" :closable="false"
      ><el-button @click="load()">重试</el-button></el-alert
    >
    <div v-loading="busy && !editor">
      <el-empty
        v-if="!error && data?.information.status !== 'SUBMITTED'"
        description="暂无已提交的收集资料"
      />
      <template v-if="data?.information.status === 'SUBMITTED'"
        ><p>{{ data.productName }} · 本次报名档案</p
        ><el-descriptions :column="1" border
          ><el-descriptions-item v-for="f in data.information.fields" :key="f.key" :label="f.label"
            ><template v-if="f.type === 'images'"
              ><template
                v-for="i in data.information.images.filter((x) => x.fieldKey === f.key)"
                :key="i.id"
                ><el-image
                  v-if="urls[i.id] && i.contentType.startsWith('image/')"
                  :src="urls[i.id]"
                  :preview-src-list="Object.values(urls)"
                  style="width: 80px"
                /><a v-else-if="urls[i.id]" :href="urls[i.id]" :download="i.name">{{ i.name }}</a><span v-else>{{ i.name }} </span></template
              ></template
            ><span v-else>{{ data.information.values[f.key] || '—' }}</span></el-descriptions-item
          ></el-descriptions
        ><el-collapse
          ><el-collapse-item title="维护记录"
            ><p v-for="h in data.history" :key="h.revision"
              >V{{ h.revision }} · {{ h.modifiedAt }} · {{ h.operatorName }} · {{ h.reason }}</p
            ><el-empty
              v-if="!data.history.length"
              description="暂无维护记录" /></el-collapse-item></el-collapse
      ></template>
    </div>
    <el-dialog
      :model-value="!!editor"
      title="维护学员档案"
      width="min(760px,95vw)"
      append-to-body
      :close-on-click-modal="false"
      :before-close="
        (done) => {
          if (!busy) {
            editor = undefined
            done()
          }
        }
      "
    >
      <p>原始提交保留，保存后生成新档案版本。未修改的字典和地区保留原标签。</p>
      <el-form label-position="top"
        ><el-form-item
          v-for="f in data?.information.fields"
          :key="f.key"
          :label="f.label"
          :required="f.required"
        >
          <template v-if="f.type === 'images'"
            ><div v-for="i in images.filter((x) => x.fieldKey === f.key)" :key="i.id"
              >{{ i.side === 'front' ? '身份证正面' : i.side === 'back' ? '身份证反面' : i.name }}
              <el-button :disabled="busy || !data?.information.canReadSensitive" @click="remove(i)"
                >移除</el-button
              ></div
            ><el-upload
              v-for="side in f.key === 'identity_images' ? ['front', 'back'] : ['']"
              :key="side"
              :accept="(f.allowedExtensions || ['jpg','jpeg','png']).map(e => '.' + e).join(',')"
              :show-file-list="false"
              :multiple="!side"
              :disabled="
                busy ||
                !data?.information.canReadSensitive ||
                (side
                  ? images.some((i) => i.fieldKey === f.key && i.side === side)
                  : images.filter((i) => i.fieldKey === f.key).length >= (f.maxImages || 9))
              "
              :before-upload="(file) => upload(f.key, side, file, f.maxSizeMb || 10, f.allowedExtensions)"
              ><el-button>{{
                side === 'front' ? '上传正面' : side === 'back' ? '上传反面' : '上传附件'
              }}</el-button></el-upload
            ></template
          >
          <el-select
            v-else-if="f.type === 'dict'"
            :model-value="value(f.key) as string"
            :disabled="
              busy || f.key === 'gender' || (f.sensitive && !data?.information.canReadSensitive)
            "
            clearable
            @update:model-value="(v) => change(f.key, v ?? '')"
            ><el-option
              v-for="o in editor?.options[f.key] || []"
              :key="o.value"
              :value="o.value"
              :label="o.label"
          /></el-select>
          <el-cascader
            v-else-if="f.type === 'area'"
            :model-value="value(f.key) as number[]"
            :options="areas"
            :props="{ value: 'id', label: 'name', children: 'children' }"
            :disabled="busy"
            @update:model-value="(v) => change(f.key, v || [])"
          />
          <el-input
            v-else
            :model-value="String(value(f.key) ?? '')"
            :type="f.type === 'textarea' ? 'textarea' : 'text'"
            :disabled="
              busy || f.key === 'age' || (f.sensitive && !data?.information.canReadSensitive)
            "
            @update:model-value="(v) => change(f.key, v)"
          /> </el-form-item
        ><el-form-item label="维护原因" required
          ><el-input
            v-model="reason"
            type="textarea"
            :maxlength="500"
            @input="renewKey" /></el-form-item
      ></el-form>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <template #footer
        ><el-button :disabled="busy" @click="editor = undefined">取消</el-button
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
