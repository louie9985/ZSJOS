<template>
  <ContentWrap>
    <div class="material-type-toolbar">
      <div>
        <h3>素材类型与模板</h3>
        <span>类型控制创建、导入、收录和推荐能力；已发布模板保持不可变</span>
      </div>
      <el-space>
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button v-hasPermi="['zsjos:material-type:create']" type="primary" @click="openType()">
          <Icon icon="ep:plus" class="mr-4px" />新增类型
        </el-button>
      </el-space>
    </div>
  </ContentWrap>

  <ContentWrap v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="mb-12px">
      <template #default><el-button link type="primary" @click="load">重试</el-button></template>
    </el-alert>
    <el-table :data="types" empty-text="暂无素材类型">
      <el-table-column prop="name" label="类型" min-width="170">
        <template #default="{ row }">
          <div class="material-type-name"><strong>{{ row.name }}</strong><span>{{ row.code }}</span></div>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="说明" min-width="220" show-overflow-tooltip />
      <el-table-column label="能力" min-width="300">
        <template #default="{ row }">
          <el-space wrap>
            <el-tag v-if="row.allowManualCreate" size="small">手工创建</el-tag>
            <el-tag v-if="row.allowImport" size="small" type="success">Excel 导入</el-tag>
            <el-tag v-if="row.allowAutoCollect" size="small" type="warning">自动收录</el-tag>
            <el-tag v-if="row.recommendationEnabled" size="small" type="primary">推荐</el-tag>
          </el-space>
        </template>
      </el-table-column>
      <el-table-column label="审批流程" min-width="180">
        <template #default="{ row }">{{ processName(row.bpmProcessDefinitionKey) }}</template>
      </el-table-column>
      <el-table-column label="当前模板" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.currentSchema" type="success">V{{ row.currentSchema.versionNo }}</el-tag>
          <el-tag v-else type="info">未发布</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'info'">{{ row.status === 0 ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button v-hasPermi="['zsjos:material-type:update']" link type="primary" @click="openType(row)">
            编辑
          </el-button>
          <el-button v-hasPermi="['zsjos:material-type:query']" link type="primary" @click="openSchema(row)">
            模板
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>

  <Dialog v-model="typeDialogVisible" :title="editingTypeId ? '编辑素材类型' : '新增素材类型'" width="720px">
    <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="120px">
      <el-form-item label="类型名称" prop="name"><el-input v-model="typeForm.name" maxlength="100" /></el-form-item>
      <el-form-item label="类型编码" prop="code">
        <el-input v-model="typeForm.code" :disabled="!!editingTypeId" placeholder="小写字母开头" />
      </el-form-item>
      <el-form-item label="说明"><el-input v-model="typeForm.description" type="textarea" maxlength="500" /></el-form-item>
      <el-form-item label="状态"><el-switch v-model="typeEnabled" active-text="启用" inactive-text="停用" /></el-form-item>
      <el-form-item label="创建能力">
        <el-checkbox v-model="typeForm.allowManualCreate">允许手工创建</el-checkbox>
        <el-checkbox v-model="typeForm.allowImport">允许 Excel 导入</el-checkbox>
        <el-checkbox v-model="typeForm.allowAutoCollect">允许审核自动收录</el-checkbox>
      </el-form-item>
      <el-form-item label="素材审批流程">
        <el-select v-model="typeForm.bpmProcessDefinitionKey" clearable filterable class="!w-100%" placeholder="不选择则不能提交手工审批">
          <el-option
            v-for="definition in materialDefinitions"
            :key="definition.id"
            :label="`${definition.name}（V${definition.version}）`"
            :value="definition.key"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="启用推荐"><el-switch v-model="typeForm.recommendationEnabled" /></el-form-item>
      <el-form-item v-if="typeForm.recommendationEnabled" label="推荐维度" required>
        <el-checkbox-group v-model="typeForm.recommendationConfig.dimensions">
          <el-checkbox value="account_type">账号类型</el-checkbox>
          <el-checkbox value="profession">专业方向</el-checkbox>
          <el-checkbox value="account_stage">账号时期</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item v-if="typeForm.recommendationEnabled" label="返回数量">
        <el-input-number v-model="typeForm.recommendationConfig.maxResults" :min="1" :max="100" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="typeDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="typeSaving" @click="saveType">保存</el-button>
    </template>
  </Dialog>

  <el-drawer v-model="schemaDrawerVisible" size="88%" :title="schemaType ? `${schemaType.name} · 模板编排` : '模板编排'">
    <template v-if="schemaType">
      <el-alert
        :title="schemaDraft ? `正在编辑模板 V${schemaDraft.versionNo} 草稿` : schemaType.currentSchema ? `保存后将创建 V${schemaType.currentSchema.versionNo + 1} 草稿` : '保存后将创建首个模板草稿'"
        type="info"
        show-icon
        :closable="false"
        class="mb-16px"
      />
      <div class="schema-actions">
        <el-space>
          <el-button v-hasPermi="['zsjos:material-schema:update']" :loading="schemaSaving" @click="saveSchema">
            保存草稿
          </el-button>
          <el-button v-hasPermi="['zsjos:material-schema:publish']" type="primary" :loading="schemaPublishing" @click="publishSchema">
            发布模板
          </el-button>
        </el-space>
      </div>
      <MaterialSchemaDesigner ref="schemaDesignerRef" v-model="schemaFields" :dict-types="dictTypes" />
      <el-divider content-position="left">历史版本</el-divider>
      <el-table :data="schemaVersions" empty-text="暂无模板版本">
        <el-table-column label="版本" width="100"><template #default="{ row }">V{{ row.versionNo }}</template></el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }"><el-tag :type="schemaStatusType(row.status)">{{ schemaStatusLabel(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="schemaHash" label="模板摘要" min-width="220" show-overflow-tooltip />
        <el-table-column prop="publishedAt" label="发布时间" min-width="170"><template #default="{ row }">{{ formatTime(row.publishedAt) }}</template></el-table-column>
        <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" @click="openSchemaHistory(row)">查看</el-button></template></el-table-column>
      </el-table>
    </template>
  </el-drawer>

  <Dialog v-model="schemaHistoryVisible" :title="historySchema ? `模板 V${historySchema.versionNo}` : '模板详情'" width="920px">
    <el-table v-if="historySchema" :data="flattenFields(historySchema.fields)" max-height="560">
      <el-table-column prop="label" label="字段" min-width="180" />
      <el-table-column prop="key" label="编码" min-width="180" />
      <el-table-column prop="type" label="组件" width="130"><template #default="{ row }">{{ fieldTypeLabel(row.type) }}</template></el-table-column>
      <el-table-column prop="dictType" label="字典" min-width="190" />
      <el-table-column label="规则" min-width="180"><template #default="{ row }">{{ fieldRulesText(row) }}</template></el-table-column>
    </el-table>
  </Dialog>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import * as DictTypeApi from '@/api/system/dict/dict.type'
import * as DictDataApi from '@/api/system/dict/dict.data'
import * as UserApi from '@/api/system/user'
import * as DeptApi from '@/api/system/dept'
import { checkPermi } from '@/utils/permission'
import * as MaterialApi from '@/api/zsjos/material'
import MaterialSchemaDesigner from './components/MaterialSchemaDesigner.vue'
import MaterialDynamicForm from '../material/components/MaterialDynamicForm.vue'

defineOptions({ name: 'ZsjosMaterialType' })

type SchemaDesignerExpose = {
  validate: () => string
  normalizedFields: () => MaterialApi.MaterialFieldDefinition[]
}
type DynamicFormExpose = { validate: () => string }

const message = useMessage()
const loading = ref(false)
const error = ref('')
const types = ref<MaterialApi.MaterialType[]>([])
const materialDefinitions = ref<MaterialApi.MaterialProcessDefinition[]>([])
const dictTypes = ref<DictTypeApi.DictTypeVO[]>([])
const dictData = ref<DictDataApi.DictDataVO[]>([])
const users = ref<UserApi.UserSimpleVO[]>([])
const departments = ref<DeptApi.DeptVO[]>([])

const typeDialogVisible = ref(false)
const editingTypeId = ref<number>()
const typeSaving = ref(false)
const typeFormRef = ref<FormInstance>()
const defaultTypeForm = (): MaterialApi.MaterialTypeSaveReq => ({
  name: '',
  code: '',
  description: '',
  status: 0,
  allowManualCreate: true,
  allowImport: false,
  allowAutoCollect: false,
  recommendationEnabled: false,
  recommendationConfig: {
    dimensions: ['account_type', 'profession', 'account_stage'],
    maxResults: 20
  },
  bpmProcessDefinitionKey: undefined,
  version: undefined
})
const typeForm = reactive<MaterialApi.MaterialTypeSaveReq>(defaultTypeForm())
const typeEnabled = computed({
  get: () => typeForm.status === 0,
  set: (enabled: boolean) => (typeForm.status = enabled ? 0 : 1)
})
const typeRules: FormRules = {
  name: [{ required: true, message: '请输入类型名称', trigger: 'blur' }],
  code: [
    { required: true, message: '请输入类型编码', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_-]{1,63}$/, message: '需以小写字母开头，只能包含小写字母、数字、下划线和短横线', trigger: 'blur' }
  ]
}

const schemaDrawerVisible = ref(false)
const schemaType = ref<MaterialApi.MaterialType>()
const schemaVersions = ref<MaterialApi.MaterialTemplate[]>([])
const schemaDraft = computed(() => schemaVersions.value.find((item) => item.status === 'DRAFT'))
const schemaFields = ref<MaterialApi.MaterialFieldDefinition[]>([])
const schemaDesignerRef = ref<SchemaDesignerExpose>()
const schemaSaving = ref(false)
const schemaPublishing = ref(false)
const schemaHistoryVisible = ref(false)
const historySchema = ref<MaterialApi.MaterialTemplate>()

const clone = <T,>(value: T): T => JSON.parse(JSON.stringify(value))
const formatTime = (value?: string) => (value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-')
const processName = (key?: string) => {
  if (!key) return '未配置'
  const definition = materialDefinitions.value.find((item) => item.key === key)
  return definition ? `${definition.name}（V${definition.version}）` : key
}
const fieldTypeLabel = (type: MaterialApi.MaterialFieldType) => ({
  text: '单行文本', textarea: '多行文本', 'rich-text': '富文本', number: '数字', date: '日期',
  datetime: '日期时间', 'dict-single': '字典单选', 'dict-multi': '字典多选', employee: '员工',
  department: '部门', image: '图片', video: '视频', attachment: '附件', 'https-link': 'HTTPS 外链',
  'repeat-group': '重复字段组'
}[type] || type)
const schemaStatusLabel = (status: string) => ({ DRAFT: '草稿', PUBLISHED: '已发布', ARCHIVED: '历史版本' }[status] || status)
const schemaStatusType = (status: string) => status === 'PUBLISHED' ? 'success' : status === 'DRAFT' ? 'warning' : 'info'

const loadBaseOptions = async () => {
  const jobs: Promise<unknown>[] = [
    MaterialApi.getMaterialTypeList().then((value) => (types.value = value)),
    MaterialApi.getMaterialProcessDefinitions().then((value) => (materialDefinitions.value = value)),
    DictTypeApi.getSimpleDictTypeList().then((value) => (dictTypes.value = value))
  ]
  await Promise.all(jobs)
}
const load = async () => {
  loading.value = true
  error.value = ''
  try {
    await loadBaseOptions()
  } catch (cause: any) {
    error.value = cause?.msg || cause?.message || '素材类型加载失败'
  } finally {
    loading.value = false
  }
}

const openType = (row?: MaterialApi.MaterialType) => {
  editingTypeId.value = row?.id
  Object.assign(typeForm, defaultTypeForm(), row ? {
    name: row.name,
    code: row.code,
    description: row.description || '',
    status: row.status,
    allowManualCreate: row.allowManualCreate,
    allowImport: row.allowImport,
    allowAutoCollect: row.allowAutoCollect,
    recommendationEnabled: row.recommendationEnabled,
    recommendationConfig: clone(row.recommendationConfig || defaultTypeForm().recommendationConfig),
    bpmProcessDefinitionKey: row.bpmProcessDefinitionKey,
    version: row.version
  } : {})
  typeDialogVisible.value = true
  nextTick(() => typeFormRef.value?.clearValidate())
}
const saveType = async () => {
  if (!(await typeFormRef.value?.validate().catch(() => false))) return
  if (typeForm.recommendationEnabled && !typeForm.recommendationConfig.dimensions.length) {
    return message.warning('启用推荐时至少选择一个推荐维度')
  }
  typeSaving.value = true
  try {
    const data = clone(typeForm)
    if (editingTypeId.value) await MaterialApi.updateMaterialType(editingTypeId.value, data)
    else await MaterialApi.createMaterialType(data)
    typeDialogVisible.value = false
    await loadBaseOptions()
    message.success('素材类型已保存')
  } catch (cause: any) {
    message.error(cause?.msg || cause?.message || '保存失败')
  } finally {
    typeSaving.value = false
  }
}

const refreshSchema = async () => {
  if (!schemaType.value) return
  const [type, versions] = await Promise.all([
    MaterialApi.getMaterialType(schemaType.value.id),
    MaterialApi.getMaterialSchemas(schemaType.value.id)
  ])
  schemaType.value = type
  schemaVersions.value = versions
  const draft = versions.find((item) => item.status === 'DRAFT')
  schemaFields.value = clone(draft?.fields || type.currentSchema?.fields || [])
}
const openSchema = async (row: MaterialApi.MaterialType) => {
  schemaType.value = row
  schemaVersions.value = []
  schemaFields.value = []
  schemaDrawerVisible.value = true
  try {
    await refreshSchema()
  } catch (cause: any) {
    message.error(cause?.msg || cause?.message || '模板加载失败')
  }
}
const persistSchema = async () => {
  if (!schemaType.value || !schemaDesignerRef.value) return false
  const problem = schemaDesignerRef.value.validate()
  if (problem) {
    message.warning(problem)
    return false
  }
  const draft = schemaDraft.value
  await MaterialApi.saveMaterialSchemaDraft(schemaType.value.id, {
    id: draft?.id,
    version: draft?.version,
    fields: schemaDesignerRef.value.normalizedFields()
  })
  await refreshSchema()
  return true
}
const saveSchema = async () => {
  schemaSaving.value = true
  try {
    if (!await persistSchema()) return
    message.success('模板草稿已保存')
  } catch (cause: any) {
    message.error(cause?.msg || cause?.message || '模板保存失败')
  } finally {
    schemaSaving.value = false
  }
}
const publishSchema = async () => {
  schemaPublishing.value = true
  try {
    if (!await persistSchema()) return
    const type = schemaType.value
    const draft = schemaDraft.value
    if (!type || !draft) throw new Error('草稿状态已变化，请刷新后重试')
    await MaterialApi.publishMaterialSchema(type.id, {
      schemaVersionId: draft.id,
      expectedSchemaVersion: draft.version,
      expectedTypeVersion: type.version
    })
    await refreshSchema()
    await loadBaseOptions()
    message.success('模板已发布')
  } catch (cause: any) {
    message.error(cause?.msg || cause?.message || '模板发布失败')
  } finally {
    schemaPublishing.value = false
  }
}
const openSchemaHistory = (schema: MaterialApi.MaterialTemplate) => {
  historySchema.value = schema
  schemaHistoryVisible.value = true
}
const flattenFields = (fields: MaterialApi.MaterialFieldDefinition[]) =>
  fields.flatMap((field) => [
    field,
    ...(field.children || []).map((child) => ({ ...child, label: `　${field.label} / ${child.label}`, key: `${field.key}.${child.key}` }))
  ])
const fieldRulesText = (field: MaterialApi.MaterialFieldDefinition) => [
  field.required ? '必填' : '',
  field.searchable ? '可检索' : '',
  field.maxLength ? `最长 ${field.maxLength}` : '',
  field.maxCount ? `最多 ${field.maxCount} 项` : ''
].filter(Boolean).join('；') || '-'

const mappingOptions = (field: MaterialApi.MaterialFieldDefinition) => {
  const text = [
    { label: '内容标题', value: 'title' },
    { label: '内容选题', value: 'topic' },
    { label: '脚本或正文', value: 'scriptText' }
  ]
  if (field.key === '__cover__') return [{ label: '封面首个文件', value: 'coverFileId' }]
  if (field.type === 'image') return [
    { label: '封面文件', value: 'coverFileIds' },
    { label: '成品预览文件', value: 'deliverableFileIds' }
  ]
  if (field.type === 'video') return [{ label: '成品预览文件', value: 'deliverableFileIds' }]
  if (field.type === 'attachment') return [
    { label: '封面文件', value: 'coverFileIds' },
    { label: '成品预览文件', value: 'deliverableFileIds' }
  ]
  if (['dict-single', 'dict-multi'].includes(field.type)) {
    const source = {
      zsjos_persona_type: { label: '账号类型', value: 'accountType' },
      zsjos_material_profession: { label: '专业方向', value: 'profession' },
      zsjos_media_account_stage: { label: '账号时期', value: 'accountStage' }
    }[field.dictType || '']
    return source ? [source] : []
  }
  if (field.type === 'datetime') return [{ label: '预计发布时间', value: 'plannedPublishAt' }]
  if (field.type === 'https-link') return [
    { label: '成品预览链接', value: 'deliverableUrl' },
    { label: '引流资料链接', value: 'leadResourceUrl' }
  ]
  if (['text', 'textarea', 'rich-text'].includes(field.type)) {
    return [...text, { label: '成品预览链接', value: 'deliverableUrl' }, { label: '引流资料链接', value: 'leadResourceUrl' }]
  }
  return []
}
onMounted(load)
</script>

<style scoped>
.material-type-toolbar,
.material-review-heading,
.schema-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.material-type-toolbar h3 {
  margin: 0 0 4px;
}

.material-type-toolbar span,
.material-review-heading span,
.material-type-name span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.material-type-name,
.material-review-heading > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.schema-actions {
  margin-bottom: 16px;
}

@media (max-width: 768px) {
  .material-type-toolbar,
  .material-review-heading {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
