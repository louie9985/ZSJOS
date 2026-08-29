<template>
  <ContentWrap>
    <el-alert
      v-if="pageError"
      class="mb-12px"
      type="error"
      show-icon
      :closable="false"
      :title="pageError"
    >
      <template #default>
        <el-button size="small" type="primary" @click="getList">重试</el-button>
      </template>
    </el-alert>

    <el-form :inline="true" :model="queryParams">
      <el-form-item label="表单名称">
        <el-input v-model="queryParams.name" clearable placeholder="请输入表单名称" @keyup.enter="getList" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" clearable placeholder="全部状态" class="!w-160px">
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已撤回" value="WITHDRAWN" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="getList">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
        <el-button v-hasPermi="['zsjos:forced-form:create']" type="primary" @click="openCreate">新建表单</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="list" empty-text="暂无强制表单">
      <el-table-column prop="name" label="名称" min-width="180" />
      <el-table-column prop="status" label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="90" />
      <el-table-column prop="recipientCount" label="接收" width="90" />
      <el-table-column prop="completedCount" label="完成" width="90" />
      <el-table-column prop="pendingCount" label="未完成" width="90" />
      <el-table-column prop="lastSentAt" label="最近发送" min-width="160">
        <template #default="{ row }">{{ formatTime(row.lastSentAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="360" fixed="right">
        <template #default="{ row }">
          <el-button v-hasPermi="['zsjos:forced-form:query']" link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button v-if="row.status === 'DRAFT'" v-hasPermi="['zsjos:forced-form:update']" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-hasPermi="['zsjos:forced-form:create']" link type="primary" @click="copy(row)">复制</el-button>
          <el-button v-if="row.status !== 'PUBLISHED'" v-hasPermi="['zsjos:forced-form:publish']" link type="primary" @click="publish(row)">发布</el-button>
          <el-button v-if="row.status === 'PUBLISHED'" v-hasPermi="['zsjos:forced-form:send']" link type="primary" @click="openSend(row)">发送</el-button>
          <el-button v-if="row.status === 'PUBLISHED'" v-hasPermi="['zsjos:forced-form:withdraw']" link type="warning" @click="withdraw(row)">撤回</el-button>
          <el-button v-if="row.status === 'DRAFT'" v-hasPermi="['zsjos:forced-form:delete']" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination v-model:page="queryParams.pageNo" v-model:limit="queryParams.pageSize" :total="total" @pagination="getList" />
  </ContentWrap>

  <ContentWrap>
    <template #header>
      <div class="forced-form-section-title">
        <span>提交记录</span>
        <el-button v-hasPermi="['zsjos:forced-form:submission-export']" :loading="exportLoading" @click="exportSubmissions">导出</el-button>
      </div>
    </template>
    <el-form :inline="true" :model="submissionQuery">
      <el-form-item label="表单">
        <el-select v-model="submissionQuery.formId" clearable filterable placeholder="全部表单" class="!w-220px">
          <el-option v-for="item in list" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="用户">
        <el-select v-model="submissionQuery.userId" clearable filterable placeholder="全部用户" class="!w-220px">
          <el-option v-for="user in users" :key="user.id" :label="user.nickname" :value="user.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="平台">
        <el-select v-model="submissionQuery.platform" clearable placeholder="全部平台" class="!w-140px">
          <el-option label="PC" value="pc" />
          <el-option label="Mobile" value="mobile" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button v-hasPermi="['zsjos:forced-form:submission-query']" type="primary" @click="getSubmissionList">查询</el-button>
        <el-button @click="resetSubmissionQuery">重置</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="submissionLoading" :data="submissionList" empty-text="暂无提交记录">
      <el-table-column prop="formName" label="表单" min-width="180" />
      <el-table-column prop="version" label="版本" width="90" />
      <el-table-column prop="userNickname" label="提交人" width="160" />
      <el-table-column prop="platform" label="平台" width="100" />
      <el-table-column prop="createTime" label="提交时间" min-width="160">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button v-hasPermi="['zsjos:forced-form:submission-read']" link type="primary" @click="openSubmission(row.id)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination v-model:page="submissionQuery.pageNo" v-model:limit="submissionQuery.pageSize" :total="submissionTotal" @pagination="getSubmissionList" />
  </ContentWrap>

  <Dialog v-model="formDialogVisible" :title="editingId ? '编辑强制表单' : '新建强制表单'" width="980px">
    <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" maxlength="128" show-word-limit />
      </el-form-item>
      <el-form-item label="说明">
        <el-input v-model="form.description" type="textarea" maxlength="500" show-word-limit />
      </el-form-item>
      <el-form-item label="字段设计">
        <div class="forced-form-designer">
          <div class="designer-toolbar">
            <el-button v-for="item in fieldTypes" :key="item.type" size="small" @click="addField(item.type)">
              {{ item.label }}
            </el-button>
          </div>
          <el-empty v-if="form.fields.length === 0" description="请添加字段" />
          <div v-for="(field, index) in form.fields" :key="index" class="designer-field">
            <div class="field-header">
              <strong>{{ index + 1 }}. {{ field.label || field.key || '未命名字段' }}</strong>
              <div>
                <el-button link :disabled="index === 0" @click="moveField(index, -1)">上移</el-button>
                <el-button link :disabled="index === form.fields.length - 1" @click="moveField(index, 1)">下移</el-button>
                <el-button link type="danger" @click="form.fields.splice(index, 1)">删除</el-button>
              </div>
            </div>
            <el-row :gutter="12">
              <el-col :span="6">
                <el-form-item :prop="`fields.${index}.key`" label="字段 key" :rules="fieldKeyRules">
                  <el-input v-model="field.key" placeholder="如 employee_confirm" />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item :prop="`fields.${index}.label`" label="标题" :rules="requiredRules">
                  <el-input v-model="field.label" />
                </el-form-item>
              </el-col>
              <el-col :span="5">
                <el-form-item label="类型">
                  <el-select v-model="field.type" @change="normalizeField(field)">
                    <el-option v-for="item in fieldTypes" :key="item.type" :label="item.label" :value="item.type" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="3">
                <el-form-item label="必填">
                  <el-switch v-model="field.required" />
                </el-form-item>
              </el-col>
              <el-col v-if="field.type === 'text' || field.type === 'textarea'" :span="4">
                <el-form-item label="最大长度">
                  <el-input-number v-model="field.maxLength" :min="1" :max="10000" />
                </el-form-item>
              </el-col>
              <el-col v-if="field.type === 'radio' || field.type === 'multi-select'" :span="7">
                <el-form-item label="System 字典">
                  <el-select v-model="field.dictType" filterable placeholder="请选择字典类型">
                    <el-option v-for="dict in dictTypes" :key="dict.type" :label="`${dict.name}（${dict.type}）`" :value="dict.type" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col v-if="field.type === 'attachment'" :span="4">
                <el-form-item label="数量">
                  <el-input-number v-model="field.maxCount" :min="1" :max="20" />
                </el-form-item>
              </el-col>
              <el-col v-if="field.type === 'attachment'" :span="4">
                <el-form-item label="大小 MB">
                  <el-input-number v-model="field.maxSizeMb" :min="1" :max="500" />
                </el-form-item>
              </el-col>
              <el-col v-if="field.type === 'attachment'" :span="8">
                <el-form-item label="扩展名">
                  <el-select v-model="field.allowedExtensions" multiple filterable allow-create default-first-option placeholder="如 pdf、jpg">
                    <el-option label="pdf" value="pdf" />
                    <el-option label="jpg" value="jpg" />
                    <el-option label="png" value="png" />
                    <el-option label="docx" value="docx" />
                    <el-option label="xlsx" value="xlsx" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
          </div>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="formDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存草稿</el-button>
    </template>
  </Dialog>

  <Dialog v-model="sendDialogVisible" title="发送强制表单" width="880px">
    <el-steps :active="sendStep" finish-status="success" simple class="mb-16px">
      <el-step title="范围选择" />
      <el-step title="接收人预览" />
      <el-step title="发送确认" />
    </el-steps>
    <el-form :model="sendForm" label-width="100px">
      <el-form-item label="发送范围">
        <el-radio-group v-model="sendForm.scopeType" @change="clearPreview">
          <el-radio-button value="ALL">全体员工</el-radio-button>
          <el-radio-button value="USERS">指定用户</el-radio-button>
          <el-radio-button value="DEPARTMENTS">指定部门</el-radio-button>
          <el-radio-button value="POSTS">指定岗位</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="sendForm.scopeType === 'USERS'" label="用户">
        <el-select v-model="sendForm.userIds" multiple filterable class="!w-100%" placeholder="请选择启用员工">
          <el-option v-for="user in users" :key="user.id" :label="user.nickname" :value="user.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="sendForm.scopeType === 'DEPARTMENTS'" label="部门">
        <el-select v-model="sendForm.deptIds" multiple filterable class="!w-100%" placeholder="请选择部门，后端会展开下级部门">
          <el-option v-for="dept in departments" :key="dept.id" :label="dept.name" :value="dept.id" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="sendForm.scopeType === 'POSTS'" label="岗位">
        <el-select v-model="sendForm.postIds" multiple filterable class="!w-100%" placeholder="请选择岗位">
          <el-option v-for="post in posts" :key="post.id" :label="post.name" :value="post.id" />
        </el-select>
      </el-form-item>
    </el-form>
    <el-alert v-if="preview" type="info" show-icon :closable="false" class="mb-12px">
      <template #title>
        预计发送 {{ preview.recipientCount }} 人；已完成跳过 {{ preview.skippedCompletedCount }} 人；重复待办过滤 {{ preview.filteredCount }} 人。
      </template>
    </el-alert>
    <el-table v-if="preview" :data="preview.recipients" max-height="300" empty-text="没有可发送接收人">
      <el-table-column prop="nickname" label="姓名" width="160" />
      <el-table-column prop="deptName" label="部门" min-width="180" />
      <el-table-column prop="postNames" label="岗位" min-width="180" />
    </el-table>
    <template #footer>
      <el-button @click="sendDialogVisible = false">取消</el-button>
      <el-button :loading="previewLoading" @click="previewRecipients">预览接收人</el-button>
      <el-button type="primary" :disabled="!preview || preview.recipientCount === 0" :loading="sending" @click="confirmSend">确认发送</el-button>
    </template>
  </Dialog>

  <Dialog v-model="detailVisible" title="强制表单详情" width="760px">
    <el-descriptions v-if="currentDetail" :column="2" border>
      <el-descriptions-item label="名称">{{ currentDetail.name }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ statusLabel(currentDetail.status) }}</el-descriptions-item>
      <el-descriptions-item label="版本">{{ currentDetail.version }}</el-descriptions-item>
      <el-descriptions-item label="当前版本ID">{{ currentDetail.currentVersionId || '-' }}</el-descriptions-item>
      <el-descriptions-item label="说明" :span="2">{{ currentDetail.description || '-' }}</el-descriptions-item>
    </el-descriptions>
    <el-table v-if="currentDetail" :data="parseFields(currentDetail.fieldsJson)" class="mt-16px">
      <el-table-column prop="key" label="key" width="160" />
      <el-table-column prop="label" label="标题" />
      <el-table-column prop="type" label="类型" width="130" />
      <el-table-column prop="dictType" label="字典" />
      <el-table-column label="必填" width="80">
        <template #default="{ row }">{{ row.required ? '是' : '否' }}</template>
      </el-table-column>
    </el-table>
  </Dialog>

  <Dialog v-model="submissionDetailVisible" title="提交详情" width="820px">
    <el-descriptions v-if="submissionDetail" :column="2" border>
      <el-descriptions-item label="表单">{{ submissionDetail.formName || submissionDetail.formId }}</el-descriptions-item>
      <el-descriptions-item label="版本">{{ submissionDetail.version || submissionDetail.versionId || '-' }}</el-descriptions-item>
      <el-descriptions-item label="提交人">{{ submissionDetail.userNickname || submissionDetail.userId }}</el-descriptions-item>
      <el-descriptions-item label="平台">{{ submissionDetail.platform || '-' }}</el-descriptions-item>
      <el-descriptions-item label="提交时间" :span="2">{{ formatTime(submissionDetail.createTime) }}</el-descriptions-item>
    </el-descriptions>
    <el-table v-if="submissionDetail" :data="submissionRows" class="mt-16px">
      <el-table-column prop="label" label="字段" width="180" />
      <el-table-column prop="display" label="提交内容" />
    </el-table>
  </Dialog>
</template>

<script setup lang="ts">
import { reactive, ref, computed } from 'vue'
import { ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { ContentWrap } from '@/components/ContentWrap'
import { Dialog } from '@/components/Dialog'
import download from '@/utils/download'
import * as UserApi from '@/api/system/user'
import * as DeptApi from '@/api/system/dept'
import * as PostApi from '@/api/system/post'
import * as DictTypeApi from '@/api/system/dict/dict.type'
import {
  copyForcedForm,
  createForcedForm,
  exportForcedFormSubmissions,
  getForcedForm,
  getForcedFormPage,
  getForcedFormSubmission,
  getForcedFormSubmissionPage,
  previewForcedFormRecipients,
  publishForcedForm,
  removeForcedForm,
  sendForcedForm,
  updateForcedForm,
  withdrawForcedForm,
  type ForcedFormField,
  type ForcedFormFieldType,
  type ForcedFormRecipientPreview,
  type ForcedFormRecord,
  type ForcedFormSubmission
} from '@/api/zsjos/forcedForm'

defineOptions({ name: 'ZsjosForcedForm' })

type PageResult<T> = { list: T[]; total: number }

const message = useMessage()
const loading = ref(false)
const pageError = ref('')
const list = ref<ForcedFormRecord[]>([])
const total = ref(0)
const queryParams = reactive({ pageNo: 1, pageSize: 20, name: '', status: '' })

const users = ref<UserApi.UserSimpleVO[]>([])
const departments = ref<DeptApi.DeptVO[]>([])
const posts = ref<PostApi.PostSimpleVO[]>([])
const dictTypes = ref<DictTypeApi.DictTypeVO[]>([])

const fieldTypes: Array<{ type: ForcedFormFieldType; label: string }> = [
  { type: 'text', label: '单行文本' },
  { type: 'textarea', label: '多行文本' },
  { type: 'radio', label: '单选字典' },
  { type: 'multi-select', label: '多选字典' },
  { type: 'checkbox', label: '确认框' },
  { type: 'attachment', label: '附件' }
]

const statusLabel = (status: string) => ({ DRAFT: '草稿', PUBLISHED: '已发布', WITHDRAWN: '已撤回' }[status] || status)
const statusTag = (status: string) => status === 'PUBLISHED' ? 'success' : status === 'WITHDRAWN' ? 'warning' : 'info'
const formatTime = (value?: number | string | Date) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'

const getList = async () => {
  loading.value = true
  pageError.value = ''
  try {
    const res = await getForcedFormPage(queryParams) as PageResult<ForcedFormRecord>
    list.value = res.list || []
    total.value = res.total || 0
  } catch (error: any) {
    pageError.value = error?.msg || error?.message || '强制表单加载失败'
  } finally {
    loading.value = false
  }
}

const resetQuery = () => {
  Object.assign(queryParams, { pageNo: 1, pageSize: 20, name: '', status: '' })
  getList()
}

const formRef = ref<FormInstance>()
const formDialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number>()
const form = reactive<{ name: string; description: string; fields: ForcedFormField[] }>({
  name: '',
  description: '',
  fields: []
})
const requiredRules = [{ required: true, message: '必填', trigger: 'blur' }]
const fieldKeyRules = [
  { required: true, message: '必填', trigger: 'blur' },
  { pattern: /^[a-z][a-z0-9_]{0,63}$/, message: '小写字母开头，仅支持小写字母、数字和下划线，最多 64 位', trigger: 'blur' }
]
const formRules = reactive<FormRules>({
  name: [{ required: true, message: '请输入表单名称', trigger: 'blur' }]
})

const emptyField = (type: ForcedFormFieldType): ForcedFormField => ({
  key: `field_${form.fields.length + 1}`,
  label: fieldTypes.find((item) => item.type === type)?.label || '字段',
  type,
  required: true,
  ...(type === 'text' || type === 'textarea' ? { maxLength: type === 'text' ? 200 : 1000 } : {}),
  ...(type === 'attachment' ? { maxCount: 1, maxSizeMb: 20, allowedExtensions: ['pdf', 'jpg', 'png'] } : {})
})
const addField = (type: ForcedFormFieldType) => form.fields.push(emptyField(type))
const moveField = (index: number, offset: number) => {
  const target = index + offset
  if (target < 0 || target >= form.fields.length) return
  const [item] = form.fields.splice(index, 1)
  form.fields.splice(target, 0, item)
}
const normalizeField = (field: ForcedFormField) => {
  delete field.dictType
  delete field.maxLength
  delete field.maxCount
  delete field.maxSizeMb
  delete field.allowedExtensions
  if (field.type === 'text') field.maxLength = 200
  if (field.type === 'textarea') field.maxLength = 1000
  if (field.type === 'attachment') {
    field.maxCount = 1
    field.maxSizeMb = 20
    field.allowedExtensions = ['pdf', 'jpg', 'png']
  }
}
const parseFields = (json?: string): ForcedFormField[] => {
  try {
    const value = JSON.parse(json || '[]')
    return Array.isArray(value) ? value : []
  } catch {
    return []
  }
}
const validateFields = () => {
  if (form.fields.length === 0 || form.fields.length > 100) throw new Error('字段数量必须在 1 到 100 之间')
  const keys = new Set<string>()
  form.fields.forEach((field) => {
    if (!field.key || !/^[a-z][a-z0-9_]{0,63}$/.test(field.key)) throw new Error(`字段 ${field.label || field.key} 的 key 不合法`)
    if (keys.has(field.key)) throw new Error(`字段 key 重复：${field.key}`)
    keys.add(field.key)
    if (!field.label) throw new Error(`字段 ${field.key} 标题不能为空`)
    if ((field.type === 'radio' || field.type === 'multi-select') && !field.dictType) throw new Error(`字段 ${field.label} 必须选择 System 字典类型`)
    if (field.type === 'attachment' && (!field.maxCount || !field.maxSizeMb)) throw new Error(`字段 ${field.label} 必须配置附件数量和大小`)
  })
}
const openCreate = () => {
  editingId.value = undefined
  Object.assign(form, { name: '', description: '', fields: [] })
  formDialogVisible.value = true
}
const openEdit = async (row: ForcedFormRecord) => {
  const detail = await getForcedForm(row.id)
  editingId.value = row.id
  Object.assign(form, { name: detail.name, description: detail.description || '', fields: parseFields(detail.fieldsJson) })
  formDialogVisible.value = true
}
const save = async () => {
  await formRef.value?.validate()
  validateFields()
  saving.value = true
  try {
    const payload = { name: form.name, description: form.description, fieldsJson: JSON.stringify(form.fields) }
    if (editingId.value) await updateForcedForm(editingId.value, payload)
    else await createForcedForm(payload)
    message.success('保存成功')
    formDialogVisible.value = false
    await getList()
  } finally {
    saving.value = false
  }
}

const currentDetail = ref<ForcedFormRecord>()
const detailVisible = ref(false)
const openDetail = async (row: ForcedFormRecord) => {
  currentDetail.value = await getForcedForm(row.id)
  detailVisible.value = true
}
const copy = async (row: ForcedFormRecord) => {
  await copyForcedForm(row.id)
  message.success('复制成功')
  await getList()
}
const publish = async (row: ForcedFormRecord) => {
  await ElMessageBox.confirm('发布后会生成不可变版本，确定发布？', '发布强制表单')
  await publishForcedForm(row.id)
  message.success('发布成功')
  await getList()
}
const withdraw = async (row: ForcedFormRecord) => {
  await ElMessageBox.confirm('撤回后员工端不再展示该表单待办，确定撤回？', '撤回强制表单')
  await withdrawForcedForm(row.id)
  message.success('撤回成功')
  await getList()
}
const remove = async (row: ForcedFormRecord) => {
  await ElMessageBox.confirm('仅草稿可删除，确定删除？', '删除强制表单')
  await removeForcedForm(row.id)
  message.success('删除成功')
  await getList()
}

const sendDialogVisible = ref(false)
const sending = ref(false)
const previewLoading = ref(false)
const sendStep = ref(0)
const sendTarget = ref<ForcedFormRecord>()
const preview = ref<ForcedFormRecipientPreview>()
type ScopeType = 'ALL' | 'USERS' | 'DEPARTMENTS' | 'POSTS'
const sendForm = reactive({
  scopeType: 'ALL' as ScopeType,
  userIds: [] as number[],
  deptIds: [] as number[],
  postIds: [] as number[]
})
const clearPreview = () => {
  preview.value = undefined
  sendStep.value = 0
}
const openSend = (row: ForcedFormRecord) => {
  sendTarget.value = row
  Object.assign(sendForm, { scopeType: 'ALL', userIds: [], deptIds: [], postIds: [] })
  preview.value = undefined
  sendStep.value = 0
  sendDialogVisible.value = true
}
const previewRecipients = async () => {
  if (!sendTarget.value) return
  previewLoading.value = true
  try {
    preview.value = await previewForcedFormRecipients(sendTarget.value.id, sendForm)
    sendStep.value = 1
  } finally {
    previewLoading.value = false
  }
}
const confirmSend = async () => {
  if (!sendTarget.value || !preview.value) return
  await ElMessageBox.confirm(`确认发送给 ${preview.value.recipientCount} 位员工？`, '发送确认')
  sending.value = true
  try {
    const result = await sendForcedForm(sendTarget.value.id, sendForm)
    sendStep.value = 2
    message.success(`发送成功，本批次接收 ${result.recipientCount} 人`)
    sendDialogVisible.value = false
    await getList()
  } finally {
    sending.value = false
  }
}

const submissionLoading = ref(false)
const submissionList = ref<ForcedFormSubmission[]>([])
const submissionTotal = ref(0)
const submissionQuery = reactive({ pageNo: 1, pageSize: 20, formId: undefined as number | undefined, userId: undefined as number | undefined, platform: '' })
const getSubmissionList = async () => {
  submissionLoading.value = true
  try {
    const res = await getForcedFormSubmissionPage(submissionQuery) as PageResult<ForcedFormSubmission>
    submissionList.value = res.list || []
    submissionTotal.value = res.total || 0
  } finally {
    submissionLoading.value = false
  }
}
const resetSubmissionQuery = () => {
  Object.assign(submissionQuery, { pageNo: 1, pageSize: 20, formId: undefined, userId: undefined, platform: '' })
  getSubmissionList()
}
const submissionDetailVisible = ref(false)
const submissionDetail = ref<ForcedFormSubmission>()
const openSubmission = async (id: number) => {
  submissionDetail.value = await getForcedFormSubmission(id)
  submissionDetailVisible.value = true
}
const submissionRows = computed(() => {
  const detail = submissionDetail.value
  if (!detail) return []
  const fields = parseFields(detail.fieldsSnapshotJson)
  const answers = safeJson(detail.answersJson) as Record<string, unknown>
  const dict = safeJson(detail.dictSnapshotJson) as Record<string, any>
  return fields.map((field) => {
    const snapshot = dict?.[field.key]
    const answer = answers?.[field.key]
    const display = snapshot?.label || snapshot?.labels?.join('、') || (Array.isArray(answer) ? answer.join('、') : String(answer ?? '-'))
    return { label: field.label, display }
  })
})
const safeJson = (json?: string) => {
  try {
    return json ? JSON.parse(json) : {}
  } catch {
    return {}
  }
}
const exportLoading = ref(false)
const exportSubmissions = async () => {
  exportLoading.value = true
  try {
    const blob = await exportForcedFormSubmissions(submissionQuery)
    download.excel(blob, '强制表单提交记录.xls')
  } finally {
    exportLoading.value = false
  }
}

const loadReferenceData = async () => {
  ;[users.value, departments.value, posts.value, dictTypes.value] = await Promise.all([
    UserApi.getSimpleUserOptions(),
    DeptApi.getSimpleDeptList(),
    PostApi.getSimplePostList(),
    DictTypeApi.getSimpleDictTypeList()
  ])
}

onMounted(async () => {
  await Promise.all([getList(), getSubmissionList(), loadReferenceData()])
})
</script>

<style scoped>
.forced-form-section-title,
.field-header,
.designer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.forced-form-designer {
  width: 100%;
}
.designer-toolbar {
  justify-content: flex-start;
  margin-bottom: 12px;
}
.designer-field {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
}
.field-header {
  margin-bottom: 8px;
}
</style>
