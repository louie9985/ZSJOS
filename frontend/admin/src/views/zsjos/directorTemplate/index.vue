<template>
  <ContentWrap>
    <div class="header-row">
      <div
        ><h2>{{ positioning ? '定位卡模板配置' : '定位访谈大纲配置' }}</h2
        ><span>发布后历史草稿继续使用原版本，修改发布内容请先复制为草稿。</span></div
      >
      <el-space
        ><el-button
          v-if="positioning"
          v-hasPermi="['zsjos:positioning-template:create']"
          @click="createTemplate"
          >新增模板</el-button
        ><el-button :loading="loading" @click="load">重试</el-button></el-space
      >
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon class="mb-16px" />
    <el-empty v-if="!loading && !templates.length" description="暂无可用模板" />
    <el-tabs v-else v-model="selectedId" @tab-change="selectTemplate">
      <el-tab-pane v-for="item in templates" :key="item.id" :name="item.id" :label="item.name" />
    </el-tabs>
    <div v-if="current" v-loading="loading" class="designer">
      <section class="field-list">
        <div class="section-title"
          ><strong>字段列表</strong
          ><el-button
            v-if="current.draft"
            v-hasPermi="[updatePermission]"
            size="small"
            @click="addField"
            >新增字段</el-button
          ></div
        >
        <button
          v-for="(field, index) in fields"
          :key="field.key"
          class="field-row"
          :class="{ active: selectedIndex === index }"
          @click="selectedIndex = index"
        >
          <span
            ><b>{{ field.title }}</b
            ><small>{{ field.key }} · {{ field.type }}</small></span
          >
          <el-space
            ><el-button link :disabled="!editable || index === 0" @click.stop="move(index, -1)"
              >上移</el-button
            ><el-button
              link
              :disabled="!editable || index === fields.length - 1"
              @click.stop="move(index, 1)"
              >下移</el-button
            ></el-space
          >
        </button>
      </section>
      <section v-if="activeField" class="properties">
        <div class="section-title"
          ><strong>字段属性</strong><el-tag v-if="activeField.systemField">系统字段</el-tag></div
        >
        <el-form label-position="top" :disabled="!editable">
          <el-form-item label="字段标题"><el-input v-model="activeField.title" /></el-form-item>
          <el-form-item label="字段编码"
            ><el-input v-model="activeField.key" :disabled="activeField.systemField"
          /></el-form-item>
          <el-form-item v-if="positioning" label="控件类型"
            ><el-select
              v-model="activeField.type"
              :disabled="activeField.systemField"
              class="w-100%"
              ><el-option
                v-for="type in fieldTypes"
                :key="type.value"
                :label="type.label"
                :value="type.value" /></el-select
          ></el-form-item>
          <el-form-item v-if="enumField" label="关联系统字典" required
            ><el-select
              v-model="activeField.dictType"
              filterable
              class="w-100%"
              @change="previewDict"
              ><el-option
                v-for="item in dictTypes"
                :key="item.type"
                :label="`${item.name} (${item.type})`"
                :value="item.type" /></el-select
            ><div class="dict-preview"
              >当前启用项 {{ dictCount }} 个<span v-if="dictError">，加载失败，请重试</span></div
          ></el-form-item
          >
          <template v-if="positioning && activeField?.type === 'material_picker'">
            <el-form-item label="素材类型"><el-select v-model="activeField.materialTypeCode" class="w-100%"><el-option label="爆款账号" value="viral_account" /><el-option label="爆款内容" value="viral_content" /></el-select></el-form-item>
            <el-form-item label="默认平台字典值"><el-input v-model="activeField.defaultPlatform" /></el-form-item>
            <el-form-item label="默认阶段字典值"><el-input v-model="activeField.defaultStage" /></el-form-item>
            <el-form-item label="推荐数量提示"><el-input v-model="activeField.recommendedCount" /></el-form-item>
            <el-form-item label="参考字段关联"><el-select v-model="activeField.referenceFor" clearable class="w-100%"><el-option v-for="target in fields.filter((x) => x.key !== activeField.key && x.type !== 'material_picker')" :key="target.key" :label="target.title" :value="target.key" /></el-select></el-form-item>
            <el-switch v-model="activeField.filterAdjustable" active-text="允许调整筛选" />
          </template>
          <el-form-item label="分组"><el-input v-model="activeField.group" /></el-form-item>
          <el-form-item label="填写备注"
            ><el-input
              v-model="activeField.description"
              type="textarea"
              :maxlength="500"
              show-word-limit
          /></el-form-item>
          <template v-if="!positioning">
            <el-form-item label="访谈注意"
              ><el-input
                v-model="activeField.interviewNote"
                type="textarea"
                :maxlength="1000"
                show-word-limit
            /></el-form-item>
            <el-space wrap
              ><el-switch v-model="activeField.allowRemark" active-text="允许访谈备注" /><el-switch
                v-model="activeField.requireAttachment"
                active-text="要求访谈稿" /><el-switch
                v-model="activeField.studentVisible"
                active-text="学员可见"
            /></el-space>
          </template>
          <el-space
            ><el-switch v-model="activeField.enabled" active-text="启用" /><el-switch
              v-model="activeField.required"
              active-text="必填"
          /></el-space>
        </el-form>
      </section>
      <section class="preview"
        ><div class="section-title"
          ><strong>{{ !positioning ? '三列访谈卡预览' : '表单预览' }}</strong
          ><el-radio-group v-model="previewMode" size="small"
            ><el-radio-button value="desktop">桌面</el-radio-button
            ><el-radio-button value="mobile">移动</el-radio-button></el-radio-group
          ></div
        ><div class="preview-body" :class="[previewMode, { 'interview-preview': !positioning }]">
          <template v-if="!positioning"
            ><div class="interview-head"
              ><span>字段</span><span>访谈注意</span><span>访谈内容确认</span></div
            ><div
              v-for="field in fields.filter((x) => x.enabled)"
              :key="field.key"
              class="interview-row"
              ><strong>{{ field.title }}{{ field.required ? ' *' : '' }}</strong
              ><span>{{ field.interviewNote || '—' }}</span
              ><div
                ><el-date-picker
                  v-if="field.type === 'date'"
                  disabled
                  placeholder="采集日期"
                /><el-input
                  v-else-if="field.systemField"
                  disabled
                  placeholder="系统学员姓名 / 编号"
                /><el-radio-group v-else v-model="previewAnswers[field.key]"
                  ><el-radio value="COMMUNICATED_DOCUMENT">已沟通，见文稿</el-radio
                  ><el-radio value="NOT_COMMUNICATED">未沟通</el-radio
                  ><el-radio value="CLIENT_REFUSED">客户拒绝回答</el-radio></el-radio-group
                ><el-input
                  v-if="field.allowRemark"
                  type="textarea"
                  placeholder="可选访谈备注"
                /><small v-if="field.requireAttachment">需关联访谈稿</small></div
              ></div
            ></template
          >
          <el-form v-else label-position="top"
            ><el-form-item
              v-for="field in fields.filter((x) => x.enabled)"
              :key="field.key"
              :label="field.title"
              :required="field.required"
              ><el-input disabled placeholder="预览控件" /><div
                v-if="field.description?.trim()"
                class="field-remark"
                >{{ field.description }}</div
              ></el-form-item
            ></el-form
          ></div
        ></section
      >
    </div>
    <el-table v-if="current" :data="current.versions" class="mt-16px"
      ><el-table-column prop="versionNo" label="版本" /><el-table-column
        prop="status"
        label="状态"
      /><el-table-column prop="publishedAt" label="发布时间" /><el-table-column label="查看"
        ><template #default="{ row }"
          ><el-button link @click="viewVersion(row)">查看版本</el-button></template
        ></el-table-column
      ></el-table
    >
    <div v-if="current" class="footer-actions"
      ><el-button @click="sync">返回当前版本</el-button
      ><el-button v-hasPermi="[updatePermission]" :loading="saving" @click="copy"
        >复制为新草稿</el-button
      ><el-button
        v-hasPermi="[updatePermission]"
        :disabled="!current.draft || viewingHistory"
        :loading="saving"
        type="primary"
        @click="save"
        >保存草稿</el-button
      ><el-button
        v-hasPermi="[publishPermission]"
        :disabled="!current.draft || viewingHistory"
        :loading="saving"
        type="success"
        @click="publishDraft"
        >发布</el-button
      ></div
    >
  </ContentWrap>
</template>
<script setup lang="ts">
import * as Api from '@/api/zsjos/director'
import { hasPermission } from '@/directives/permission/hasPermi'
import * as DictTypeApi from '@/api/system/dict/dict.type'
import * as DictDataApi from '@/api/system/dict/dict.data'
import { useMessage } from '@/hooks/web/useMessage'
const route = useRoute(),
  message = useMessage()
const positioning = computed(
  () => String(route.path).includes('positioning') && !String(route.path).includes('interview')
)
const updatePermission = computed(() =>
  positioning.value
    ? 'zsjos:positioning-template:update'
    : 'zsjos:director-interview-template:update'
)
const publishPermission = computed(() =>
  positioning.value
    ? 'zsjos:positioning-template:publish'
    : 'zsjos:director-interview-template:publish'
)
const saving = ref(false),
  viewingHistory = ref(false),
  previewAnswers = ref<Record<string, string>>({})
const loading = ref(false),
  error = ref(''),
  templates = ref<Api.DirectorTemplate[]>([]),
  selectedId = ref<number>(),
  fields = ref<Api.DirectorField[]>([]),
  selectedIndex = ref(0),
  previewMode = ref('desktop'),
  dictTypes = ref<any[]>([]),
  dictCount = ref(0),
  dictError = ref(false)
const current = computed(() => templates.value.find((x) => x.id === selectedId.value))
const editable = computed(
  () =>
    !!current.value?.draft &&
    !viewingHistory.value &&
    !saving.value &&
    hasPermission([updatePermission.value])
)
const activeField = computed(() => fields.value[selectedIndex.value])
const enumField = computed(() =>
  ['select', 'multi_select', 'radio', 'checkbox_group'].includes(activeField.value?.type || '')
)
const fieldTypes = [
  { value: 'text', label: '单行文本' },
  { value: 'textarea', label: '多行文本' },
  { value: 'number', label: '数字' },
  { value: 'select', label: '下拉单选' },
  { value: 'multi_select', label: '下拉多选' },
  { value: 'radio', label: '单选' },
  { value: 'checkbox_group', label: '多选' },
  { value: 'checkbox', label: '开关' },
  { value: 'region', label: '地区' },
  { value: 'attachment', label: '附件' },
  { value: 'material_picker', label: '素材选择' },
  { value: 'system_history', label: '系统历史（只读）' }
]
const sync = () => {
  viewingHistory.value = false
  const v = current.value?.draft || current.value?.published
  fields.value = (v?.fields || []).map((x) => ({ ...x }))
  selectedIndex.value = 0
  void previewDict()
}
const viewVersion = (v: Api.TemplateVersion) => {
  viewingHistory.value = true
  fields.value = v.fields.map((x) => ({ ...x }))
  selectedIndex.value = 0
}
const load = async () => {
  loading.value = true
  error.value = ''
  try {
    templates.value = await Api.getTemplates(positioning.value)
    if (!templates.value.some((x) => x.id === selectedId.value))
      selectedId.value = templates.value[0]?.id
    sync()
    if (positioning.value) dictTypes.value = await DictTypeApi.getSimpleDictTypeList()
  } catch (e: any) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}
const selectTemplate = () => sync()
const move = (i: number, d: number) => {
  if (!editable.value) return
  const a = [...fields.value]
  ;[a[i], a[i + d]] = [a[i + d], a[i]]
  fields.value = a.map((x, n) => ({ ...x, sort: (n + 1) * 10 }))
  selectedIndex.value = i + d
}
const addField = () => {
  if (!editable.value) return
  fields.value.push({
    key: `custom_${Date.now()}`,
    title: '新字段',
    type: 'text',
    enabled: true,
    required: false,
    systemField: false,
    allowRemark: true,
    requireAttachment: false,
    studentVisible: false,
    sort: (fields.value.length + 1) * 10
  })
  selectedIndex.value = fields.value.length - 1
}
const previewDict = async () => {
  dictCount.value = 0
  dictError.value = false
  if (!activeField.value?.dictType) return
  try {
    dictCount.value = (await DictDataApi.getDictDataByType(activeField.value.dictType)).filter(
      (x: any) => x.status === 0
    ).length
  } catch {
    dictError.value = true
  }
}
const mutate = async (action: () => Promise<void>) => {
  saving.value = true
  error.value = ''
  try {
    await action()
  } catch (e: any) {
    error.value = e?.message || '操作失败，请重试'
  } finally {
    saving.value = false
  }
}
const copy = () =>
  mutate(async () => {
    if (!current.value) return
    await Api.copyDraft(positioning.value, current.value.id, current.value.version)
    await load()
  })
const save = () =>
  mutate(async () => {
    const t = current.value,
      v = t?.draft
    if (!t || !v || viewingHistory.value) return message.warning('请先复制为草稿')
    if (
      fields.value.some((x) => !x.key.trim() || !x.title.trim()) ||
      new Set(fields.value.map((x) => x.key)).size !== fields.value.length
    )
      return message.error('字段标题和编码不能为空，编码不能重复')
    if (
      positioning.value &&
      fields.value.some(
        (x) => ['select', 'multi_select', 'radio', 'checkbox_group'].includes(x.type) && !x.dictType
      )
    )
      return message.error('枚举字段必须关联系统字典')
    await Api.saveDraft(positioning.value, t.id, {
      versionId: v.id,
      version: v.version,
      name: t.name,
      defaultTemplate: t.defaultTemplate,
      fields: fields.value
    })
    message.success('草稿已保存')
    await load()
  })
const publishDraft = () =>
  mutate(async () => {
    const t = current.value,
      v = t?.draft
    if (!t || !v || viewingHistory.value) return message.warning('没有可发布草稿')
    if (JSON.stringify(fields.value) !== JSON.stringify(v.fields))
      return message.warning('请先保存修改后再发布')
    await Api.publish(positioning.value, t.id, { versionId: v.id, version: v.version })
    message.success('已发布')
    await load()
  })
const createTemplate = async () => {
  await Api.createPositioning({
    templateCode: `positioning_${Date.now()}`,
    name: '新定位卡模板',
    defaultTemplate: false,
    fields: fields.value
  })
  message.success('模板已创建')
  await load()
}
watch(() => route.path, load)
onMounted(load)
</script>
<style scoped>
.header-row,
.section-title,
.footer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.header-row {
  margin-bottom: 20px;
}

.header-row h2 {
  margin: 0 0 6px;
}

.header-row span {
  color: var(--el-text-color-secondary);
}

.designer {
  display: grid;
  grid-template-columns: minmax(250px, 1fr) minmax(280px, 1fr) minmax(300px, 1.2fr);
  gap: 16px;
}

.field-list,
.properties,
.preview {
  min-height: 480px;
  padding: 16px;
  border: 1px solid var(--el-border-color);
}

.field-row {
  display: flex;
  width: 100%;
  padding: 12px;
  text-align: left;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  justify-content: space-between;
  align-items: center;
}

.field-row.active {
  background: var(--el-color-primary-light-9);
}

.field-row small {
  display: block;
  margin-top: 4px;
  color: var(--el-text-color-secondary);
}

.dict-preview,
.field-remark {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.field-remark {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.preview-body.mobile {
  max-width: 360px;
}

.interview-head,
.interview-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.4fr) minmax(0, 1.6fr);
  gap: 12px;
  align-items: start;
}

.interview-head {
  padding: 8px 0;
  font-weight: 600;
  border-bottom: 1px solid var(--el-border-color);
}

.interview-row {
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.interview-row span {
  color: var(--el-text-color-secondary);
  white-space: pre-wrap;
}

.interview-row :deep(.el-radio-group) {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.interview-preview.mobile .interview-head {
  display: none;
}

.interview-preview.mobile .interview-row {
  grid-template-columns: minmax(0, 1fr);
}

.interview-row > * {
  min-width: 0;
  overflow-wrap: anywhere;
}

.interview-row :deep(.el-radio) {
  height: auto;
  margin-right: 0;
  white-space: normal;
}

.interview-row :deep(.el-radio__label) {
  min-width: 0;
  white-space: normal;
  overflow-wrap: anywhere;
}

.footer-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (width <= 1000px) {
  .footer-actions :deep(.el-button) {
    margin-left: 0;
  }

  .designer {
    grid-template-columns: 1fr;
  }

  .field-list,
  .properties,
  .preview {
    min-height: auto;
  }
}
</style>
