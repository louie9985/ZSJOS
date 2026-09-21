<template>
  <ContentWrap>
    <el-alert
      v-if="optionsError"
      :title="optionsError"
      type="error"
      show-icon
      :closable="false"
      class="mb-12px"
    >
      <template #default>
        <el-button link type="primary" :loading="optionsLoading" @click="loadOptions">
          重试加载基础选项
        </el-button>
      </template>
    </el-alert>
    <el-form :inline="true" :model="query">
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" clearable placeholder="标题、摘要或可检索字段" @keyup.enter="search" />
      </el-form-item>
      <el-form-item label="素材类型">
        <el-select v-model="query.materialTypeId" clearable filterable class="!w-190px">
          <el-option v-for="type in types" :key="type.id" :label="type.name" :value="type.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable class="!w-150px">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="来源">
        <el-select v-model="query.source" clearable class="!w-150px">
          <el-option label="手工创建" value="MANUAL" />
          <el-option label="Excel 导入" value="IMPORT" />
          <el-option label="内容审核收录" value="CONTENT_REVIEW" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
        <el-button
          v-hasPermi="['zsjos:material:create']"
          type="primary"
          :disabled="!optionsReady"
          @click="openCreate"
        >
          <Icon icon="ep:plus" class="mr-4px" />创建素材
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap v-loading="loading">
    <el-alert v-if="listError" :title="listError" type="error" show-icon :closable="false" class="mb-12px">
      <template #default><el-button link type="primary" @click="load">重试</el-button></template>
    </el-alert>
    <el-table :data="rows" empty-text="暂无素材">
      <el-table-column prop="materialNo" label="素材编号" width="170" />
      <el-table-column label="素材" min-width="260">
        <template #default="{ row }">
          <div class="material-title-cell">
            <el-image v-if="row.coverPreviewUrl" :src="row.coverPreviewUrl" fit="cover" />
            <div><strong>{{ row.title }}</strong><span>{{ row.summary || '无摘要' }}</span></div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="materialTypeName" label="类型" width="140" />
      <el-table-column label="来源" width="130"><template #default="{ row }">{{ sourceLabel(row.source) }}</template></el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="ownerName" label="创建人" width="130" />
      <el-table-column label="使用数据" width="190">
        <template #default="{ row }">点赞 {{ row.likeCount }} · 收藏 {{ row.favoriteCount }} · 调用 {{ row.referenceCount }}</template>
      </el-table-column>
      <el-table-column label="排序" width="110">
        <template #default="{ row }"><span v-if="row.pinned">置顶 · </span>{{ row.priority }}</template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" min-width="170"><template #default="{ row }">{{ formatTime(row.updateTime) }}</template></el-table-column>
      <el-table-column label="操作" width="290" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button
            v-if="row.availableActions.includes('UPDATE')"
            v-hasPermi="['zsjos:material:update']"
            link
            type="primary"
            :disabled="!optionsReady"
            @click="openEdit(row)"
          >编辑</el-button>
          <el-button v-if="row.availableActions.includes('SUBMIT')" v-hasPermi="['zsjos:material:submit']" link type="primary" @click="submit(row)">提交审批</el-button>
          <el-button v-if="row.availableActions.includes('DISABLE')" v-hasPermi="['zsjos:material:disable']" link type="danger" @click="disable(row)">停用</el-button>
          <el-button v-if="row.availableActions.includes('RESTORE')" v-hasPermi="['zsjos:material:restore']" link type="primary" @click="restore(row)">恢复</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination v-model:page="query.pageNo" v-model:limit="query.pageSize" :total="total" @pagination="load" />
  </ContentWrap>

  <el-drawer v-model="editorVisible" size="82%" :title="editingId ? '编辑素材' : '创建素材'" destroy-on-close>
    <el-alert
      v-if="editorError"
      :title="editorError"
      type="error"
      show-icon
      :closable="false"
      class="mb-12px"
    />
    <el-form ref="editorFormRef" :model="editorForm" :rules="editorRules" label-width="110px">
      <el-row :gutter="18">
        <el-col :xs="24" :lg="12">
          <el-form-item label="素材类型" prop="materialTypeId">
            <el-select
              v-model="editorForm.materialTypeId"
              :disabled="!!editingId"
              filterable
              class="!w-100%"
              @change="changeEditorType"
            >
              <el-option
                v-for="type in creatableTypes"
                :key="type.id"
                :label="type.name"
                :value="type.id"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :lg="12">
          <el-form-item label="标题" prop="title"><el-input v-model="editorForm.title" maxlength="255" /></el-form-item>
        </el-col>
        <el-col :xs="24" :lg="16">
          <el-form-item label="摘要"><el-input v-model="editorForm.summary" type="textarea" :rows="3" maxlength="2000" show-word-limit /></el-form-item>
        </el-col>
        <el-col :xs="24" :lg="8">
          <el-form-item label="封面">
            <div class="cover-upload">
              <el-image v-if="coverUpload?.previewUrl" :src="coverUpload.previewUrl" :preview-src-list="[coverUpload.previewUrl]" fit="cover" />
              <div v-else-if="editorForm.coverFileId" class="cover-upload__placeholder">文件 {{ editorForm.coverFileId }}</div>
            <ClipboardUploadActions :disabled="coverUploading || Boolean(editorForm.coverFileId)" :can-paste="!coverUploading && !editorForm.coverFileId" @files="handlePasteCover"><el-upload :show-file-list="false" :http-request="uploadCover" accept="image/*">
                <el-button :loading="coverUploading"><Icon icon="ep:upload" class="mr-4px" />上传附件</el-button>
              </el-upload></ClipboardUploadActions>
              <el-button v-if="editorForm.coverFileId" link type="danger" @click="clearCover">移除</el-button>
            </div>
          </el-form-item>
        </el-col>
        <el-col :xs="12" :lg="6">
          <el-form-item label="置顶"><el-switch v-model="editorForm.pinned" /></el-form-item>
        </el-col>
        <el-col :xs="12" :lg="6">
          <el-form-item label="人工优先级"><el-input-number v-model="editorForm.priority" :min="-10000" :max="10000" /></el-form-item>
        </el-col>
      </el-row>
      <el-divider content-position="left">素材内容</el-divider>
      <el-empty v-if="!editorFields.length" description="请先选择已发布模板的素材类型" />
      <MaterialDynamicForm
        v-else
        ref="dynamicFormRef"
        v-model="editorForm.values"
        :fields="editorFields"
        :dict-data="dictData"
        :users="users"
        :departments="departments"
        :files="editorFiles"
      />
    </el-form>
    <template #footer>
      <el-button @click="editorVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存草稿</el-button>
    </template>
  </el-drawer>

  <el-drawer v-model="detailVisible" size="92%" title="查看爆款拆解" class="material-detail-drawer">
    <div v-loading="detailLoading">
      <el-alert v-if="detailError" :title="detailError" type="error" show-icon :closable="false">
        <template #default><el-button link type="primary" @click="detailId && loadDetail(detailId)">重试</el-button></template>
      </el-alert>
      <template v-else-if="detail">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="素材编号">{{ detail.materialNo }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ detail.materialTypeName }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
          <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ detail.ownerName || detail.ownerUserId }}</el-descriptions-item>
          <el-descriptions-item label="摘要" :span="3">{{ detail.summary || '-' }}</el-descriptions-item>
          <el-descriptions-item label="来源">{{ sourceLabel(detail.source) }}</el-descriptions-item>
          <el-descriptions-item label="点赞 / 收藏 / 调用">{{ detail.likeCount }} / {{ detail.favoriteCount }} / {{ detail.referenceCount }}</el-descriptions-item>
          <el-descriptions-item label="更新">{{ formatTime(detail.updateTime) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.disabledReason" label="停用原因" :span="3">{{ detail.disabledReason }}</el-descriptions-item>
        </el-descriptions>
        <div class="detail-version-toolbar">
          <strong>内容版本</strong>
          <el-select v-model="detailVersionId" class="!w-240px" @change="changeDetailVersion">
            <el-option
              v-for="version in detailVersions"
              :key="version.id"
              :label="`V${version.versionNo} · ${versionStatusLabel(version.status)}`"
              :value="version.id"
            />
          </el-select>
        </div>
        <el-alert v-if="detailVersion?.rejectionReason" :title="detailVersion.rejectionReason" type="error" show-icon :closable="false" class="mb-12px" />
        <el-descriptions v-if="detailVersion" :column="3" border class="mb-16px">
          <el-descriptions-item label="版本">V{{ detailVersion.versionNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ versionStatusLabel(detailVersion.status) }}</el-descriptions-item>
          <el-descriptions-item label="流程版本">{{ detailVersion.processDefinitionVersion ? `V${detailVersion.processDefinitionVersion}` : '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ formatTime(detailVersion.submittedAt) }}</el-descriptions-item>
          <el-descriptions-item label="生效时间">{{ formatTime(detailVersion.effectiveAt) }}</el-descriptions-item>
          <el-descriptions-item label="流程实例">{{ detailVersion.processInstanceId || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="detailVersion" class="material-detail-layout">
          <el-card class="material-detail-cover-card" shadow="never">
            <el-image v-if="detailCoverUrl" :src="detailCoverUrl" fit="contain" :preview-src-list="[detailCoverUrl]" />
            <el-empty v-else description="暂无封面" />
          </el-card>
          <el-card v-for="section in ['ACCOUNT_DETAIL', 'DIRECTOR_ANALYSIS', 'BUILD_SUGGESTION']" :key="section" shadow="never" class="material-detail-section">
            <template #header>{{ section === 'ACCOUNT_DETAIL' ? '账号详情' : section === 'DIRECTOR_ANALYSIS' ? '编导拆解' : '搭建建议' }}</template>
            <MaterialDynamicForm :model-value="detailVersion.values" :snapshots="detailVersion.dictSnapshot" :fields="detailVersion.fields.filter((field) => (field.section || 'ACCOUNT_DETAIL') === section)" :dict-data="dictData" :users="users" :departments="departments" :files="detailVersion.files" readonly />
          </el-card>
        </div>
      </template>
    </div>
    <template #footer>
      <div class="material-detail-actions">
        <el-button v-if="detail?.availableActions.includes('UPDATE')" v-hasPermi="['zsjos:material:update']" @click="detail && openEdit(detail)">编辑</el-button>
        <el-button v-if="detail?.availableActions.includes('SUBMIT')" v-hasPermi="['zsjos:material:submit']" type="primary" @click="detail && submit(detail)">提交审批</el-button>
        <el-button v-if="detailApproval" v-hasPermi="['zsjos:material-approval:approve']" type="success" @click="decideApproval('approve')">审批通过</el-button>
        <el-button v-if="detailApproval" v-hasPermi="['zsjos:material-approval:reject']" type="danger" @click="decideApproval('reject')">驳回</el-button>
        <el-button v-if="detail?.availableActions.includes('DISABLE')" v-hasPermi="['zsjos:material:disable']" type="danger" @click="detail && disable(detail)">停用</el-button>
        <el-button v-if="detail?.availableActions.includes('RESTORE')" v-hasPermi="['zsjos:material:restore']" @click="detail && restore(detail)">恢复</el-button>
        <el-button @click="detailVisible = false">关闭</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ElMessageBox, type FormInstance, type FormRules, type UploadRequestOptions } from 'element-plus'
import * as DictDataApi from '@/api/system/dict/dict.data'
import * as UserApi from '@/api/system/user'
import * as DeptApi from '@/api/system/dept'
import * as MaterialApi from '@/api/zsjos/material'
import MaterialDynamicForm from './components/MaterialDynamicForm.vue'
import ClipboardUploadActions from '@/components/UploadFile/src/ClipboardUploadActions.vue'

defineOptions({ name: 'ZsjosMaterial' })

type DynamicFormExpose = { validate: () => string }
type EditorForm = MaterialApi.MaterialSaveReq & { summary: string }

const message = useMessage()
const loading = ref(false)
const listError = ref('')
const optionsLoading = ref(false)
const optionsError = ref('')
const optionsLoaded = ref(false)
const rows = ref<MaterialApi.Material[]>([])
const total = ref(0)
const types = ref<MaterialApi.MaterialType[]>([])
const dictData = ref<DictDataApi.DictDataVO[]>([])
const users = ref<UserApi.UserSimpleVO[]>([])
const departments = ref<DeptApi.DeptVO[]>([])
const query = reactive<MaterialApi.MaterialPageParams>({ pageNo: 1, pageSize: 20 })

const editorVisible = ref(false)
const editorError = ref('')
const editingId = ref<number>()
const editorMaterial = ref<MaterialApi.Material>()
const editorFields = ref<MaterialApi.MaterialFieldDefinition[]>([])
const editorFiles = ref<MaterialApi.MaterialFile[]>([])
const editorFormRef = ref<FormInstance>()
const dynamicFormRef = ref<DynamicFormExpose>()
const saving = ref(false)
const coverUploading = ref(false)
const coverUpload = ref<MaterialApi.MaterialUpload>()
const defaultEditor = (): EditorForm => ({
  materialTypeId: undefined as unknown as number,
  title: '',
  coverFileId: undefined,
  summary: '',
  values: {},
  pinned: false,
  priority: 0,
  expectedMaterialVersion: undefined
})
const editorForm = reactive<EditorForm>(defaultEditor())
const handlePasteCover = (files: File[]) => { const file = files[0]; if (file) void uploadCover({ file } as UploadRequestOptions) }
const editorRules: FormRules = {
  materialTypeId: [{ required: true, message: '请选择素材类型', trigger: 'change' }],
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }]
}

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detailId = ref<number>()
const detail = ref<MaterialApi.Material>()
const detailVersions = ref<MaterialApi.MaterialVersion[]>([])
const detailVersionId = ref<number>()
const detailVersion = ref<MaterialApi.MaterialVersion>()
const detailApproval = ref<MaterialApi.MaterialApprovalTask>()

const statusOptions = [
  { label: '草稿', value: 'DRAFT' },
  { label: '审批中', value: 'IN_APPROVAL' },
  { label: '已生效', value: 'EFFECTIVE' },
  { label: '已驳回', value: 'REJECTED' },
  { label: '已停用', value: 'DISABLED' }
]
const creatableTypes = computed(() => types.value.filter((type) => type.status === 0 && type.allowManualCreate && type.currentSchema))
const optionsReady = computed(() => optionsLoaded.value && !optionsError.value)
// 详情封面优先取当前版本预览图，回退到材料封面；仅在两者都缺失时为空
const detailCoverUrl = computed(() => detailVersion.value?.coverPreviewUrl || detail.value?.coverPreviewUrl || '')
const clone = <T,>(value: T): T => JSON.parse(JSON.stringify(value))
const formatTime = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
const statusLabel = (value: string) => statusOptions.find((item) => item.value === value)?.label || value
const statusType = (value: string) => value === 'EFFECTIVE' ? 'success' : value === 'IN_APPROVAL' ? 'warning' : value === 'REJECTED' || value === 'DISABLED' ? 'danger' : 'info'
const versionStatusLabel = (value: string) => ({ DRAFT: '草稿', IN_APPROVAL: '审批中', EFFECTIVE: '已生效', REJECTED: '已驳回' }[value] || value)
const sourceLabel = (value: string) => ({ MANUAL: '手工创建', IMPORT: 'Excel 导入', CONTENT_REVIEW: '内容审核收录' }[value] || value)

const loadOptions = async () => {
  optionsLoading.value = true
  optionsError.value = ''
  optionsLoaded.value = false
  try {
    const results = await Promise.allSettled([
      MaterialApi.getMaterialTypeList().then((value) => (types.value = value)),
      DictDataApi.getSimpleDictDataList().then((value) => (dictData.value = value)),
      UserApi.getSimpleUserOptions().then((value) => (users.value = value)),
      DeptApi.getSimpleDeptList().then((value) => (departments.value = value))
    ])
    const failure = results.find((result) => result.status === 'rejected')
    if (failure?.status === 'rejected') {
      const cause = failure.reason as any
      optionsError.value = cause?.msg || cause?.message || '素材基础选项加载失败'
      return
    }
    optionsLoaded.value = true
  } finally {
    optionsLoading.value = false
  }
}
const load = async () => {
  loading.value = true
  listError.value = ''
  try {
    const page = await MaterialApi.getMaterialPage(query)
    rows.value = page.list || []
    total.value = page.total || 0
  } catch (cause: any) {
    rows.value = []
    total.value = 0
    listError.value = cause?.msg || cause?.message || '素材列表加载失败'
  } finally {
    loading.value = false
  }
}
const search = () => {
  query.pageNo = 1
  void load()
}
const resetQuery = () => {
  Object.assign(query, { pageNo: 1, pageSize: 20, keyword: undefined, materialTypeId: undefined, status: undefined, source: undefined })
  void load()
}

const openCreate = () => {
  if (!optionsReady.value) return message.warning('请先重新加载素材基础选项')
  editingId.value = undefined
  editorMaterial.value = undefined
  editorFields.value = []
  editorFiles.value = []
  coverUpload.value = undefined
  editorError.value = ''
  Object.assign(editorForm, defaultEditor())
  editorVisible.value = true
  nextTick(() => editorFormRef.value?.clearValidate())
}
const changeEditorType = (id: number) => {
  const type = types.value.find((item) => item.id === id)
  editorFields.value = clone(type?.currentSchema?.fields || [])
  editorForm.values = {}
}
const openEdit = async (row: MaterialApi.Material) => {
  if (!optionsReady.value) return message.warning('请先重新加载素材基础选项')
  editorVisible.value = true
  editorError.value = ''
  editingId.value = row.id
  try {
    const material = await MaterialApi.getMaterial(row.id)
    const version = material.currentVersion
    const type = types.value.find((item) => item.id === material.materialTypeId)
    if (!version || !type?.currentSchema) throw new Error('素材内容版本或模板不存在')
    editorMaterial.value = material
    const fields = version.status === 'DRAFT' ? version.fields : type.currentSchema.fields
    const knownKeys = new Set(fields.map((field) => field.key))
    const values = Object.fromEntries(Object.entries(version.values || {}).filter(([key]) => knownKeys.has(key)))
    editorFields.value = clone(fields)
    editorFiles.value = version.files || []
    coverUpload.value = material.coverFileId ? {
      fileId: material.coverFileId,
      name: '当前封面',
      contentType: 'image/*',
      size: 0,
      previewUrl: material.coverPreviewUrl
    } : undefined
    Object.assign(editorForm, {
      materialTypeId: material.materialTypeId,
      title: version.title || material.title,
      coverFileId: version.coverFileId,
      summary: version.summary || '',
      values: clone(values),
      pinned: material.pinned,
      priority: material.priority,
      expectedMaterialVersion: material.version
    })
  } catch (cause: any) {
    editorError.value = cause?.msg || cause?.message || '素材详情加载失败'
  }
}
const uploadCover = async (options: UploadRequestOptions) => {
  coverUploading.value = true
  try {
    const file = await MaterialApi.uploadMaterialFile(options.file)
    editorForm.coverFileId = file.fileId
    coverUpload.value = file
    options.onSuccess?.(file)
  } catch (cause) {
    throw cause
  } finally {
    coverUploading.value = false
  }
}
const clearCover = () => {
  editorForm.coverFileId = undefined
  coverUpload.value = undefined
}
const save = async () => {
  if (!(await editorFormRef.value?.validate().catch(() => false))) return
  const problem = dynamicFormRef.value?.validate()
  if (problem) return message.warning(problem)
  saving.value = true
  try {
    const data = clone(editorForm)
    if (editingId.value) await MaterialApi.updateMaterial(editingId.value, data)
    else await MaterialApi.createMaterial(data)
    editorVisible.value = false
    await load()
    message.success('素材草稿已保存')
  } catch (cause: any) {
    message.error(cause?.msg || cause?.message || '素材保存失败')
  } finally {
    saving.value = false
  }
}
const submit = async (row: MaterialApi.Material) => {
  await useMessage().confirm(`提交素材“${row.title}”进入审批？`)
  try {
    await MaterialApi.submitMaterial(row.id, row.version)
    await load()
    message.success('素材已提交审批')
  } catch (cause: any) {
    message.error(cause?.msg || cause?.message || '提交审批失败')
  }
}
const disable = async (row: MaterialApi.Material) => {
  const result = await ElMessageBox.prompt('请输入停用原因', `停用素材“${row.title}”`, {
    confirmButtonText: '确认停用', cancelButtonText: '取消', inputType: 'textarea',
    inputValidator: (value) => value.trim().length > 0 && value.trim().length <= 500 || '请输入 1 至 500 字停用原因'
  })
  try {
    await MaterialApi.disableMaterial(row.id, row.version, result.value.trim())
    await load()
    message.success('素材已停用')
  } catch (cause: any) {
    message.error(cause?.msg || cause?.message || '停用失败')
  }
}
const restore = async (row: MaterialApi.Material) => {
  await useMessage().confirm(`恢复素材“${row.title}”并重新进入检索与推荐？`)
  try {
    await MaterialApi.restoreMaterial(row.id, row.version)
    await load()
    message.success('素材已恢复')
  } catch (cause: any) {
    message.error(cause?.msg || cause?.message || '恢复失败')
  }
}

const openDetail = (row: MaterialApi.Material) => {
  detailVisible.value = true
  void loadDetail(row.id)
}
const loadDetail = async (id: number) => {
  detailId.value = id
  detailLoading.value = true
  detailError.value = ''
  try {
    const [material, versions] = await Promise.all([
      MaterialApi.getMaterial(id),
      MaterialApi.getMaterialVersions(id)
    ])
    detail.value = material
    detailVersions.value = versions
    detailVersion.value = material.currentVersion || versions[0]
    detailVersionId.value = detailVersion.value?.id
    detailApproval.value = undefined
    for (const type of types.value.filter((item) => item.code === 'viral_account' || item.code === 'viral_content')) {
      const page = await MaterialApi.getMaterialApprovalPage(type.code)
      const match = page.list.find((item) => item.materialNo === material.materialNo)
      if (match) { detailApproval.value = match; break }
    }
  } catch (cause: any) {
    detail.value = undefined
    detailError.value = cause?.msg || cause?.message || '素材详情加载失败'
  } finally {
    detailLoading.value = false
  }
}
const changeDetailVersion = async (id: number) => {
  detailLoading.value = true
  try {
    detailVersion.value = await MaterialApi.getMaterialVersion(id)
  } catch (cause: any) {
    message.error(cause?.msg || cause?.message || '版本加载失败')
  } finally {
    detailLoading.value = false
  }
}
const decideApproval = async (action: 'approve' | 'reject') => {
  if (!detailApproval.value || !detailVersion.value || !detailId.value) return
  const result = await ElMessageBox.prompt('请输入审批意见', action === 'approve' ? '通过素材审批' : '驳回素材审批', { inputType: 'textarea', inputValidator: (value) => value.trim().length > 0 || '审批意见不能为空' })
  await MaterialApi.decideMaterialApproval(action, detailApproval.value.versionId, detailApproval.value.task.id, result.value.trim())
  message.success(action === 'approve' ? '审批已通过' : '素材已驳回')
  await loadDetail(detailId.value)
  await load()
}

onMounted(async () => {
  await Promise.all([loadOptions(), load()])
})
</script>

<style scoped>
.material-title-cell {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.material-title-cell .el-image {
  flex: 0 0 64px;
  width: 64px;
  height: 48px;
  border-radius: 4px;
}

.material-title-cell > div {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.material-title-cell strong,
.material-title-cell span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.material-title-cell span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.cover-upload {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.cover-upload .el-image,
.cover-upload__placeholder {
  width: 120px;
  height: 80px;
  border-radius: 4px;
}

.cover-upload__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
}

.detail-version-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 20px 0 12px;
}

.material-detail-layout { display: grid; grid-template-columns: minmax(180px, .8fr) repeat(3, minmax(260px, 1fr)); gap: 14px; align-items: start; }
.material-detail-layout > .el-card { min-height: 520px; }
.material-detail-cover-card :deep(.el-card__body) { padding: 12px; }
.material-detail-cover-card .el-image { width: 100%; max-height: 620px; }
.material-detail-section { overflow: hidden; }
.material-detail-section :deep(.el-card__body) { max-height: 620px; overflow: auto; }
.material-detail-actions { display: flex; justify-content: flex-start; gap: 10px; width: 100%; }
@media (max-width: 1200px) { .material-detail-layout { grid-template-columns: repeat(2, minmax(260px, 1fr)); } }
@media (max-width: 720px) { .material-detail-layout { grid-template-columns: 1fr; } }
</style>
