<template>
  <ContentWrap>
    <div class="toolbar">
      <el-button type="primary" v-hasPermi="['zsjos:work-order-scene:create']" @click="openForm()"
        >新建模板</el-button
      >
      <el-button @click="load">刷新</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" show-icon class="mb-12px"
      ><template #default><el-button link @click="load">重试</el-button></template></el-alert
    >
    <el-table v-loading="loading" :data="list">
      <el-table-column prop="code" label="编码" min-width="140" /><el-table-column
        prop="name"
        label="名称"
        min-width="160"
      />
      <el-table-column prop="categoryLabel" label="分类" width="120" /><el-table-column
        prop="processorType"
        label="处理器"
        width="150"
      />
      <el-table-column label="状态" width="110"
        ><template #default="{ row }"
          ><el-tag
            :type="
              row.lifecycleStatus === 'DISABLED'
                ? 'info'
                : row.lifecycleStatus === 'PUBLISHED'
                  ? 'success'
                  : 'warning'
            "
            >{{ statusLabel[row.lifecycleStatus || 'DRAFT'] }}</el-tag
          ></template
        ></el-table-column
      >
      <el-table-column prop="publishedVersionNo" label="发布版本" width="100" />
      <el-table-column label="操作" width="310" fixed="right"
        ><template #default="{ row }">
          <el-button
            link
            type="primary"
            v-hasPermi="['zsjos:work-order-scene:update']"
            @click="openForm(row)"
            >编辑草稿</el-button
          >
          <el-button
            link
            type="success"
            v-hasPermi="['zsjos:work-order-scene:publish']"
            @click="publish(row)"
            >发布</el-button
          >
          <el-button link @click="showVersions(row)">版本</el-button>
          <el-button
            v-if="row.status === 1"
            link
            type="danger"
            v-hasPermi="['zsjos:work-order-scene:disable']"
            @click="disable(row)"
            >停用</el-button
          >
        </template></el-table-column
      >
    </el-table>
    <Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load"
    />
  </ContentWrap>

  <el-dialog
    v-model="formOpen"
    :title="form.id ? '编辑模板草稿' : '新建工单模板'"
    width="1000px"
    top="4vh"
  >
    <el-form :model="form" label-width="120px"
      ><el-tabs v-model="tab">
        <el-tab-pane label="基本设置" name="basic"
          ><div class="grid">
            <el-form-item label="模板编码" required
              ><el-input v-model="form.code" :disabled="Boolean(form.id)"
            /></el-form-item>
            <el-form-item label="模板名称" required><el-input v-model="form.name" /></el-form-item>
            <el-form-item label="工单分类" required
              ><el-select v-model="form.categoryValue"
                ><el-option
                  v-for="item in categories"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value" /></el-select
            ></el-form-item>
            <el-form-item label="处理器" required
              ><el-select v-model="form.processorType"
                ><el-option label="通用工单" value="GENERIC" /><el-option
                  label="拍剪工单"
                  value="PRODUCTION_TICKET" /></el-select
            ></el-form-item>
            <el-form-item label="图标"><el-input v-model="form.icon" /></el-form-item
            ><el-form-item label="排序"
              ><el-input-number v-model="form.sort" :min="0"
            /></el-form-item>
            <el-form-item label="模板说明" class="wide"
              ><el-input v-model="form.remark" type="textarea" :rows="3"
            /></el-form-item> </div
        ></el-tab-pane>
        <el-tab-pane label="资格与流转" name="scope"
          ><div class="grid">
            <el-form-item label="发起资格" required
              ><el-select v-model="form.sourceQualificationMode"
                ><el-option
                  v-for="item in qualificationOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value" /></el-select
            ></el-form-item>
            <el-form-item label="接收资格" required
              ><el-select v-model="form.targetQualificationMode"
                ><el-option
                  v-for="item in qualificationOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value" /></el-select
            ></el-form-item>
            <el-form-item v-if="usesRole(form.sourceQualificationMode)" label="发起角色" required
              ><el-select v-model="form.sourceRoleIds" multiple filterable
                ><el-option
                  v-for="item in roles"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id" /></el-select
            ></el-form-item>
            <el-form-item v-if="usesRole(form.targetQualificationMode)" label="接收角色" required
              ><el-select v-model="form.targetRoleIds" multiple filterable
                ><el-option
                  v-for="item in roles"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id" /></el-select
            ></el-form-item>
            <el-form-item v-if="usesDept(form.sourceQualificationMode)" label="发起部门" required
              ><el-select v-model="form.sourceDeptIds" multiple filterable
                ><el-option
                  v-for="item in departments"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id" /></el-select
            ></el-form-item>
            <el-form-item v-if="usesDept(form.targetQualificationMode)" label="接收部门" required
              ><el-select v-model="form.targetDeptIds" multiple filterable
                ><el-option
                  v-for="item in departments"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id" /></el-select
            ></el-form-item>
            <el-form-item label="允许指派" required
              ><el-checkbox-group v-model="form.allowedAssignmentTypes"
                ><el-checkbox value="PERSON">指定人</el-checkbox
                ><el-checkbox value="DEPARTMENT">指定部门</el-checkbox></el-checkbox-group
              ></el-form-item
            >
            <el-form-item label="拒单策略" required
              ><el-select v-model="form.rejectionStrategy"
                ><el-option label="工单失效" value="INVALID" /><el-option
                  label="进入角色池"
                  value="ROLE_POOL" /><el-option
                  label="进入部门池"
                  value="DEPARTMENT_POOL" /></el-select
            ></el-form-item> </div
        ></el-tab-pane>
        <el-tab-pane label="编号规则" name="number"
          ><div class="grid">
            <el-form-item label="编号前缀" required
              ><el-input v-model="form.numberPrefix" maxlength="12"
            /></el-form-item>
            <el-form-item label="重置周期" required
              ><el-select v-model="form.numberResetPeriod"
                ><el-option label="每日" value="DAILY" /><el-option
                  label="每月"
                  value="MONTHLY" /><el-option label="每年" value="YEARLY" /><el-option
                  label="不重置"
                  value="NONE" /></el-select
            ></el-form-item>
            <el-form-item label="流水位数" required
              ><el-input-number v-model="form.numberSequenceWidth" :min="4" :max="8"
            /></el-form-item> </div
        ></el-tab-pane>
        <el-tab-pane label="动态表单" name="fields">
          <el-table :data="form.fields"
            ><el-table-column label="字段键"
              ><template #default="{ row }"
                ><el-input v-model="row.key" /></template></el-table-column
            ><el-table-column label="名称"
              ><template #default="{ row }"
                ><el-input v-model="row.label" /></template></el-table-column
            ><el-table-column label="类型" width="160"
              ><template #default="{ row }"
                ><el-select v-model="row.type"
                  ><el-option
                    v-for="item in fieldTypes"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value" /></el-select></template></el-table-column
            ><el-table-column label="字典类型"
              ><template #default="{ row }"
                ><el-select v-if="row.type === 'dictionary'" v-model="row.dictionaryType" filterable
                  ><el-option
                    v-for="item in dictTypes"
                    :key="item.type"
                    :label="item.name"
                    :value="item.type" /></el-select
                ><span v-else>-</span></template
              ></el-table-column
            ><el-table-column label="必填" width="80"
              ><template #default="{ row }"
                ><el-switch v-model="row.required" /></template></el-table-column
            ><el-table-column width="70"
              ><template #default="{ $index }"
                ><el-button link type="danger" @click="form.fields.splice($index, 1)"
                  >删除</el-button
                ></template
              ></el-table-column
            ></el-table
          >
          <el-button
            class="mt-12px"
            @click="form.fields.push({ key: '', label: '', type: 'text', required: false })"
            >新增字段</el-button
          >
        </el-tab-pane>
      </el-tabs></el-form
    >
    <template #footer
      ><el-button @click="formOpen = false">取消</el-button
      ><el-button type="primary" :loading="saving" @click="save">保存草稿</el-button></template
    >
  </el-dialog>
  <el-dialog v-model="versionsOpen" title="发布版本" width="760px"
    ><el-table :data="versions"
      ><el-table-column prop="publishedVersionNo" label="版本" width="90" /><el-table-column
        prop="name"
        label="模板名称" /><el-table-column prop="processorType" label="处理器" /><el-table-column
        prop="publishedAt"
        label="发布时间" /></el-table
  ></el-dialog>
</template>

<script setup lang="ts">
import { cloneDeep } from 'lodash-es'
import * as DeptApi from '@/api/system/dept'
import * as RoleApi from '@/api/system/role'
import * as DictTypeApi from '@/api/system/dict/dict.type'
import { getStrDictOptions } from '@/utils/dict'
import * as Api from '@/api/zsjos/workOrder'
defineOptions({ name: 'ZsjosWorkOrderTemplate' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const list = ref<Api.WorkOrderScene[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 20 })
const formOpen = ref(false)
const versionsOpen = ref(false)
const versions = ref<Api.WorkOrderScene[]>([])
const tab = ref('basic')
const roles = ref<RoleApi.RoleVO[]>([])
const departments = ref<DeptApi.DeptVO[]>([])
const dictTypes = ref<DictTypeApi.DictTypeVO[]>([])
const categories = computed(() => getStrDictOptions('zsjos_work_order_category'))
const statusLabel: Record<string, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  DISABLED: '已停用'
}
const qualificationOptions = [
  { value: 'ROLE', label: '任一角色' },
  { value: 'DEPARTMENT', label: '任一部门' },
  { value: 'ROLE_AND_DEPARTMENT', label: '角色且部门' }
]
const fieldTypes = [
  { value: 'text', label: '单行文本' },
  { value: 'textarea', label: '多行文本' },
  { value: 'number', label: '数字' },
  { value: 'date', label: '日期' },
  { value: 'datetime', label: '日期时间' },
  { value: 'user', label: '用户' },
  { value: 'department', label: '部门' },
  { value: 'dictionary', label: '字典' },
  { value: 'attachment', label: '附件' }
]
const empty = (): Api.WorkOrderScene => ({
  code: '',
  name: '',
  processorType: 'GENERIC',
  categoryValue: '',
  icon: '',
  sort: 0,
  fields: [],
  allowedAssignmentTypes: ['PERSON'],
  sourceQualificationMode: 'ROLE',
  sourceRoleIds: [],
  sourceDeptIds: [],
  targetQualificationMode: 'ROLE',
  targetRoleIds: [],
  targetDeptIds: [],
  rejectionStrategy: 'INVALID',
  numberPrefix: 'WO',
  numberResetPeriod: 'DAILY',
  numberSequenceWidth: 6,
  status: 1
})
const form = reactive<Api.WorkOrderScene>(empty())
const usesRole = (mode?: string) => mode === 'ROLE' || mode === 'ROLE_AND_DEPARTMENT'
const usesDept = (mode?: string) => mode === 'DEPARTMENT' || mode === 'ROLE_AND_DEPARTMENT'
const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const page = await Api.getWorkOrderScenePage(query)
    list.value = page.list || []
    total.value = page.total || 0
  } catch (e: any) {
    error.value = e?.msg || e?.message || '模板加载失败'
  } finally {
    loading.value = false
  }
}
const openForm = (row?: Api.WorkOrderScene) => {
  Object.assign(form, empty(), row ? cloneDeep(row) : {})
  tab.value = 'basic'
  formOpen.value = true
}
const valid = () =>
  Boolean(
    form.code.trim() &&
    form.name.trim() &&
    form.categoryValue &&
    form.allowedAssignmentTypes?.length &&
    (!usesRole(form.sourceQualificationMode) || form.sourceRoleIds?.length) &&
    (!usesDept(form.sourceQualificationMode) || form.sourceDeptIds?.length) &&
    (!usesRole(form.targetQualificationMode) || form.targetRoleIds?.length) &&
    (!usesDept(form.targetQualificationMode) || form.targetDeptIds?.length)
  )
const save = async () => {
  if (!valid()) return message.warning('请完整配置基本信息、资格范围和指派方式')
  saving.value = true
  try {
    form.id ? await Api.updateWorkOrderScene(form) : await Api.createWorkOrderScene(form)
    message.success('模板草稿已保存')
    formOpen.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const publish = async (row: Api.WorkOrderScene) => {
  const validation = await Api.validateWorkOrderScenePublish(row.id!)
  await ElMessageBox.confirm(
    `编号示例：${validation.numberPreview}。发布后该版本不可修改，仅影响新工单。`,
    '发布模板',
    { type: 'warning' }
  )
  await Api.publishWorkOrderScene(row.id!, row.version!)
  message.success('模板已发布')
  await load()
}
const disable = async (row: Api.WorkOrderScene) => {
  await ElMessageBox.confirm('停用后将禁止新发起，运行中和历史工单不受影响。', '停用模板', {
    type: 'warning'
  })
  await Api.disableWorkOrderScene(row.id!, row.version!)
  message.success('模板已停用')
  await load()
}
const showVersions = async (row: Api.WorkOrderScene) => {
  versions.value = await Api.getWorkOrderSceneVersions(row.id!)
  versionsOpen.value = true
}
onMounted(async () => {
  ;[roles.value, departments.value, dictTypes.value] = await Promise.all([
    RoleApi.getSimpleRoleList(),
    DeptApi.getSimpleDeptList(),
    DictTypeApi.getSimpleDictTypeList()
  ])
  await load()
})
</script>
<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}
.wide {
  grid-column: 1/-1;
}
.el-select,
.el-input-number {
  width: 100%;
}
@media (width <= 800px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .wide {
    grid-column: auto;
  }
}
</style>
