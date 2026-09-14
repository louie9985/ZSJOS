<template>
  <ContentWrap>
    <div class="toolbar">
      <div>
        <h3>第三方账号字段配置</h3>
        <span>发布后，新建或再次编辑账号时使用新版本；历史快照保持不变</span>
      </div>
      <div>
        <el-button
          v-hasPermi="['zsjos:media-account-field-config:update']"
          :disabled="!!config?.draft || !config?.published"
          :loading="copying"
          @click="copy"
          >复制已发布版本</el-button
        >
        <el-button
          v-hasPermi="['zsjos:media-account-field-config:update']"
          type="primary"
          :disabled="!draft"
          :loading="saving"
          @click="save"
          >保存草稿</el-button
        >
        <el-button
          v-hasPermi="['zsjos:media-account-field-config:publish']"
          :disabled="!draft || staleDraft"
          :loading="publishing"
          @click="publish"
          >发布</el-button
        >
      </div>
    </div>
  </ContentWrap>

  <ContentWrap v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" show-icon>
      <template #default><el-button link type="primary" @click="load">重试</el-button></template>
    </el-alert>
    <el-empty v-else-if="!draft" description="暂无草稿，请先复制已发布版本" />
    <template v-else>
      <el-alert
        v-if="staleDraft"
        type="warning"
        title="当前草稿早于已发布版本，请先合并最新字段后再发布"
        :closable="false"
        ><el-button
          v-hasPermi="['zsjos:media-account-field-config:update']"
          :loading="saving"
          @click="reconcile"
          >保留草稿并合并最新字段</el-button
        ></el-alert
      >
      <div class="section-heading">
        <div>
          <h4>字段定义</h4>
          <span>当前草稿版本 V{{ draft.versionNo }}</span>
        </div>
        <el-button type="primary" plain @click="addField">
          <Icon icon="ep:plus" class="mr-5px" />新增字段
        </el-button>
      </div>
      <el-table :data="draft.fields" row-key="key">
        <el-table-column label="顺序" width="116">
          <template #default="{ $index }">
            <el-button link :disabled="$index === 0" @click="move($index, -1)">上移</el-button>
            <el-button link :disabled="$index === draft.fields.length - 1" @click="move($index, 1)"
              >下移</el-button
            >
          </template>
        </el-table-column>
        <el-table-column label="字段 key" min-width="180">
          <template #default="{ row }"
            ><el-input v-model="row.key" placeholder="例如 homepage_url"
          /></template>
        </el-table-column>
        <el-table-column label="字段名称" min-width="160">
          <template #default="{ row }"><el-input v-model="row.label" /></template>
        </el-table-column>
        <el-table-column label="填写提示" min-width="240">
          <template #default="{ row }"><el-input v-model="row.description" type="textarea" :maxlength="2000" /></template>
        </el-table-column>
        <el-table-column label="参考素材配置" min-width="260">
          <template #default="{ row }"><div v-if="row.type === 'materials'">
            <el-select v-model="row.referenceFor" placeholder="关联填写字段"><el-option v-for="target in draft.fields.filter(f => f.type === 'textarea')" :key="target.key" :label="target.label" :value="target.key" /></el-select>
            <el-select v-model="row.materialTypeCode" placeholder="素材类型"><el-option label="爆款账号" value="viral_account" /><el-option label="爆款内容" value="viral_content" /></el-select>
            <el-input v-model="row.defaultPlatform" placeholder="默认平台字典值（可留空）" />
            <el-input v-model="row.defaultAccountStage" placeholder="默认适用阶段字典值（可留空）" />
            <el-input v-model="row.recommendedCount" placeholder="推荐数量，仅提示" />
          </div></template>
        </el-table-column>
        <el-table-column label="类型" width="150">
          <template #default="{ row }">
            <el-select v-model="row.type" @change="onTypeChange(row)">
              <el-option
                v-for="item in fieldTypes"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="关联字典" min-width="190">
          <template #default="{ row }">
            <el-select
              v-if="row.type === 'select' || row.type === 'multi_select'"
              v-model="row.dictType"
              filterable
              placeholder="选择系统字典"
            >
              <el-option
                v-for="item in dictTypes"
                :key="item.type"
                :label="item.name"
                :value="item.type"
              />
            </el-select>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="填写责任" width="160"
          ><template #default="{ row }"
            ><el-select v-model="row.ownerType" @change="onOwnerChange(row)"
              ><el-option label="系统自动 · 红" value="AUTO" /><el-option
                label="编导 · 蓝"
                value="DIRECTOR" /><el-option label="运营 · 黄" value="OPERATOR" /><el-option
                label="待确认"
                value="UNASSIGNED" /></el-select></template
        ></el-table-column>
        <el-table-column label="展示分区" width="155"
          ><template #default="{ row }"
            ><el-select v-model="row.group"
              ><el-option label="账号资料" value="PROFILE" /><el-option
                label="学员与定位"
                value="POSITIONING" /><el-option label="账号状态" value="STATUS" /><el-option
                label="经营指标"
                value="METRICS" /><el-option
                label="复盘与交付"
                value="REVIEW" /></el-select></template
        ></el-table-column>
        <el-table-column label="数据来源" width="150"
          ><template #default="{ row }"
            ><el-select v-model="row.sourceType" :disabled="row.ownerType !== 'AUTO'"
              ><el-option label="人工填写" value="MANUAL" /><el-option
                label="账号数据"
                value="ACCOUNT" /><el-option label="学员档案" value="STUDENT" /><el-option
                label="等待接入"
                value="PENDING" /></el-select></template
        ></el-table-column>
        <el-table-column label="计入待补" width="90"
          ><template #default="{ row }"
            ><el-switch
              v-model="row.requiredForComplete"
              :disabled="
                !['DIRECTOR', 'OPERATOR'].includes(row.ownerType) || row.type === 'record'
              " /></template
        ></el-table-column>
        <el-table-column label="检索" width="76">
          <template #default="{ row }"><el-switch v-model="row.searchable" /></template>
        </el-table-column>
        <el-table-column label="启用" width="76">
          <template #default="{ row }"><el-switch v-model="row.enabled" /></template>
        </el-table-column>
        <el-table-column label="操作" width="76">
          <template #default="{ $index }"
            ><el-button link type="danger" @click="remove($index)">删除</el-button></template
          >
        </el-table-column>
      </el-table>
    </template>
  </ContentWrap>
</template>

<script lang="ts" setup>
import * as Api from '@/api/zsjos/mediaAccountFieldConfig'
import * as DictTypeApi from '@/api/system/dict/dict.type'

defineOptions({ name: 'ZsjosMediaAccountFieldConfig' })

const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const copying = ref(false)
const publishing = ref(false)
const error = ref('')
const config = ref<Api.AccountFieldConfig>()
const dictTypes = ref<DictTypeApi.DictTypeVO[]>([])
const draft = computed(() => config.value?.draft)
const staleDraft = computed(
  () =>
    !!draft.value &&
    !!config.value?.published &&
    draft.value.versionNo <= config.value.published.versionNo
)
const fieldTypes: Array<{ value: Api.FieldType; label: string }> = [
  { value: 'text', label: '单行文本' },
  { value: 'textarea', label: '多行文本' },
  { value: 'number', label: '数字' },
  { value: 'date', label: '日期' },
  { value: 'select', label: '单选字典' },
  { value: 'multi_select', label: '多选字典' },
  { value: 'boolean', label: '是/否' },
  { value: 'image', label: '图片' },
  { value: 'attachment', label: '附件上传' },
  { value: 'materials', label: '参考素材' },
  { value: 'record', label: '追加记录' },
  { value: 'url', label: '主页链接' }
]

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    ;[config.value, dictTypes.value] = await Promise.all([
      Api.getAccountFieldConfig(),
      DictTypeApi.getSimpleDictTypeList()
    ])
  } catch (e: any) {
    error.value = e?.msg || e?.message || '配置加载失败'
  } finally {
    loading.value = false
  }
}
const addField = () =>
  draft.value?.fields.push({
    key: `custom_${crypto.randomUUID().replaceAll('-', '')}`,
    label: '新字段',
    type: 'text',
    required: false,
    ownerType: 'UNASSIGNED',
    group: 'PROFILE',
    sourceType: 'MANUAL',
    requiredForCreate: false,
    requiredForComplete: false,
    snapshotPolicy: 'ON_SELECTION',
    enabled: true,
    searchable: false,
    sort: 0
  })
const move = (index: number, offset: number) => {
  if (!draft.value) return
  const target = index + offset
  if (target < 0 || target >= draft.value.fields.length) return
  ;[draft.value.fields[index], draft.value.fields[target]] = [
    draft.value.fields[target],
    draft.value.fields[index]
  ]
}
const remove = (index: number) => draft.value?.fields.splice(index, 1)
const onOwnerChange = (field: Api.AccountField) => {
  field.sourceType = field.ownerType === 'AUTO' ? 'PENDING' : 'MANUAL'
  if (!['DIRECTOR', 'OPERATOR'].includes(field.ownerType)) field.requiredForComplete = false
}
const onTypeChange = (field: Api.AccountField) => {
  if (field.type === 'record') field.requiredForComplete = false
  if (field.type !== 'select' && field.type !== 'multi_select') field.dictType = undefined
}
const normalizedFields = () =>
  draft.value?.fields.map((field, index) => ({
    ...field,
    key: field.key.trim(),
    label: field.label.trim(),
    sort: (index + 1) * 10
  })) || []
const validate = (fields: Api.AccountField[]) => {
  if (!fields.length || !fields.some((field) => field.enabled)) return '至少需要一个启用字段'
  if (fields.some((field) => !/^[a-z][a-z0-9_]{0,63}$/.test(field.key)))
    return '字段 key 需以小写字母开头，仅包含小写字母、数字和下划线'
  if (new Set(fields.map((field) => field.key)).size !== fields.length) return '字段 key 不能重复'
  if (fields.some((field) => !field.label)) return '字段名称不能为空'
  if (
    fields.some(
      (field) => (field.type === 'select' || field.type === 'multi_select') && !field.dictType
    )
  )
    return '字典字段必须选择系统字典'
  return ''
}
const copy = async () => {
  const published = config.value?.published
  if (!published) return
  copying.value = true
  try {
    await Api.copyAccountFieldDraft(published.id, published.version)
    await load()
    message.success('已创建草稿')
  } catch (e: any) {
    message.error(e?.msg || e?.message || '复制失败')
  } finally {
    copying.value = false
  }
}
const persist = async () => {
  if (!draft.value) return false
  const fields = normalizedFields()
  const problem = validate(fields)
  if (problem) {
    message.warning(problem)
    return false
  }
  await Api.saveAccountFieldDraft({ id: draft.value.id, version: draft.value.version, fields })
  return true
}
const reconcile = async () => {
  saving.value = true
  try {
    if (!(await persist())) return
    config.value = await Api.getAccountFieldConfig()
    if (!draft.value) return
    await Api.reconcileAccountFieldDraft(draft.value.id, draft.value.version)
    await load()
    message.success('已保留草稿内容并合并最新字段，请检查责任后发布')
  } catch (e: any) {
    message.error(e?.msg || e?.message || '合并失败')
  } finally {
    saving.value = false
  }
}
const save = async () => {
  saving.value = true
  try {
    if (!(await persist())) return
    await load()
    message.success('草稿已保存')
  } catch (e: any) {
    message.error(e?.msg || e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}
const publish = async () => {
  publishing.value = true
  try {
    if (!(await persist())) return
    config.value = await Api.getAccountFieldConfig()
    const current = config.value.draft
    if (!current) throw new Error('草稿状态已变化，请刷新后重试')
    await Api.publishAccountFieldConfig(current.id, current.version)
    await load()
    message.success('字段配置已发布')
  } catch (e: any) {
    message.error(e?.msg || e?.message || '发布失败')
  } finally {
    publishing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar,
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.toolbar h3,
.section-heading h4 {
  margin: 0;
}

.toolbar span,
.section-heading span {
  color: var(--el-text-color-secondary);
}
</style>
