<template>
  <ContentWrap>
    <div class="template-heading">
      <div>
        <h3>高级筛选预置</h3>
        <p>为工作台页面维护系统预置快捷筛选，条件以结构化筛选树保存。</p>
      </div>
      <el-button
        v-hasPermi="['zsjos:advanced-filter-template:update']"
        type="primary"
        :disabled="catalogError || !fields.length"
        @click="openCreate"
      >
        新增预置
      </el-button>
    </div>
  </ContentWrap>

  <ContentWrap>
    <el-form :inline="true" :model="query">
      <el-form-item label="适用页面">
        <el-select v-model="selectedPageKey" filterable class="!w-300px" @change="changePage">
          <el-option
            v-for="page in pageOptions"
            :key="page.pageKey"
            :label="page.label"
            :value="page.pageKey"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="筛选场景">
        <el-select :model-value="query.scene" disabled class="!w-180px">
          <el-option
            v-for="scene in sceneOptions"
            :key="scene.value"
            :label="scene.label"
            :value="scene.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadAll">查询</el-button>
        <el-button @click="resetPage">重置</el-button>
      </el-form-item>
    </el-form>

    <el-alert
      v-if="error"
      class="mb-12px"
      type="error"
      show-icon
      :closable="false"
      :title="error"
    >
      <template #default>
        <el-button link type="primary" @click="loadAll">重试</el-button>
      </template>
    </el-alert>

    <el-alert
      v-else-if="catalogError"
      class="mb-12px"
      type="error"
      show-icon
      :closable="false"
      title="高级筛选字段目录加载失败"
    >
      <template #default>
        <el-button link type="primary" @click="loadCatalog">重试</el-button>
      </template>
    </el-alert>

    <el-table v-loading="loading || catalogLoading" :data="templates" empty-text="暂无系统预置">
      <el-table-column prop="name" label="名称" min-width="180" show-overflow-tooltip />
      <el-table-column label="页面" min-width="180">
        <template #default="{ row }">{{ pageLabel(row.pageKey) }}</template>
      </el-table-column>
      <el-table-column label="筛选场景" width="130">
        <template #default="{ row }">{{ sceneLabel(row.scene) }}</template>
      </el-table-column>
      <el-table-column label="条件数" width="90">
        <template #default="{ row }">{{ countGroup(row.filter) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">
            {{ row.enabled ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="默认" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.defaultTemplate" type="warning" effect="plain">默认</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="90" />
      <el-table-column label="更新时间" min-width="170">
        <template #default="{ row }">{{ formatNullableDate(row.updateTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button
            v-hasPermi="['zsjos:advanced-filter-template:update']"
            link
            type="primary"
            @click="openEdit(row)"
          >
            编辑
          </el-button>
          <el-button
            v-hasPermi="['zsjos:advanced-filter-template:update']"
            link
            type="danger"
            @click="remove(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>

  <Dialog v-model="dialogVisible" :title="form.id ? '编辑系统预置' : '新增系统预置'" width="920px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="适用页面" prop="pageKey">
            <el-select
              v-model="form.pageKey"
              filterable
              class="!w-100%"
              :disabled="Boolean(form.id)"
              @change="changeFormPage"
            >
              <el-option
                v-for="page in pageOptions"
                :key="page.pageKey"
                :label="page.label"
                :value="page.pageKey"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="模板名称" prop="name">
            <el-input v-model="form.name" maxlength="30" show-word-limit />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="排序" prop="sort">
            <el-input-number v-model="form.sort" :min="0" :max="9999" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="状态">
            <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="默认预置">
            <el-switch v-model="form.defaultTemplate" active-text="是" inactive-text="否" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="筛选条件">
        <div class="filter-editor">
          <div v-if="catalogLoading" class="catalog-state">
            <el-icon class="is-loading"><Loading /></el-icon><span>正在加载可筛选字段</span>
          </div>
          <el-alert
            v-else-if="catalogError"
            type="error"
            title="筛选字段加载失败"
            show-icon
            :closable="false"
          >
            <template #default>
              <el-button link type="primary" @click="loadCatalog">重试</el-button>
            </template>
          </el-alert>
          <el-empty v-else-if="!fields.length" description="当前场景没有可用筛选字段" />
          <ZsjosAdvancedFilterGroup
            v-else
            :model-value="form.filter"
            :fields="fields"
            :depth="0"
            :total="countGroup(form.filter)"
            @update:model-value="updateFilter"
            @retry-options="retryOptions"
          />
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button
        type="primary"
        :loading="saving"
        :disabled="catalogError || !fields.length"
        @click="save"
      >
        保存
      </el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import { Loading } from '@element-plus/icons-vue'
import { ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { ContentWrap } from '@/components/ContentWrap'
import { Dialog } from '@/components/Dialog'
import * as AdvancedFilterApi from '@/api/zsjos/advancedFilter'
import * as TemplateApi from '@/api/zsjos/advancedFilterTemplate'
import * as DictDataApi from '@/api/system/dict/dict.data'
import * as UserApi from '@/api/system/user'
import { formatNullableDate } from '@/utils/formatTime'
import ZsjosAdvancedFilterGroup from '../components/ZsjosAdvancedFilterGroup.vue'

defineOptions({ name: 'ZsjosAdvancedFilterTemplate' })

type PageOption = {
  label: string
  scene: AdvancedFilterApi.AdvancedFilterScene
  pageKey: string
}

const sceneOptions: Array<{ label: string; value: AdvancedFilterApi.AdvancedFilterScene }> = [
  { label: '客资', value: 'lead' },
  { label: '成交订单', value: 'order' },
  { label: '无效申诉', value: 'lead_appeal' },
  { label: '重复复核', value: 'duplicate_review' },
  { label: '报名池', value: 'registration' },
  { label: '我的学员', value: 'student' },
  { label: '下属销售', value: 'subordinate_sales' }
]

const pageOptions: PageOption[] = [
  { label: '客资管理', scene: 'lead', pageKey: 'lead_management' },
  { label: '抢单池', scene: 'lead', pageKey: 'lead_claim_pool' },
  { label: '老客资协作池', scene: 'lead', pageKey: 'lead_aging_pool' },
  { label: '下属销售 · 名下客资', scene: 'lead', pageKey: 'subordinate_sales_leads' },
  { label: '我的订单', scene: 'order', pageKey: 'sales_order_my' },
  { label: '团队订单', scene: 'order', pageKey: 'sales_order_team' },
  { label: '订单审批 · 报名中心', scene: 'order', pageKey: 'sales_order_approval:registration' },
  { label: '订单审批 · 财务中心', scene: 'order', pageKey: 'sales_order_approval:finance' },
  { label: '销售主管确认', scene: 'order', pageKey: 'sales_order_supervisor_confirm' },
  { label: '无效申诉', scene: 'lead_appeal', pageKey: 'lead_appeal' },
  { label: '重复客资复核', scene: 'duplicate_review', pageKey: 'lead_duplicate_review' },
  { label: '报名池', scene: 'registration', pageKey: 'registration_pool' },
  { label: '我的学员', scene: 'student', pageKey: 'student_my' },
  { label: '下属销售', scene: 'subordinate_sales', pageKey: 'subordinate_sales' }
]

const message = useMessage()
const selectedPageKey = ref(pageOptions[0].pageKey)
const query = reactive({
  scene: pageOptions[0].scene,
  pageKey: pageOptions[0].pageKey
})
const templates = ref<AdvancedFilterApi.AdvancedFilterTemplate[]>([])
const fields = ref<AdvancedFilterApi.AdvancedFilterField[]>([])
const loading = ref(false)
const catalogLoading = ref(false)
const saving = ref(false)
const error = ref('')
const catalogError = ref(false)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()
const blank = (): AdvancedFilterApi.AdvancedFilterGroup => ({ logic: 'AND', conditions: [], groups: [] })
const form = reactive<AdvancedFilterApi.AdvancedFilterTemplateSaveReq>({
  scene: query.scene,
  pageKey: query.pageKey,
  name: '',
  filter: blank(),
  sort: 10,
  enabled: true,
  defaultTemplate: false
})
const rules = reactive<FormRules>({
  pageKey: [{ required: true, message: '请选择适用页面', trigger: 'change' }],
  name: [{ required: true, message: '请输入模板名称', trigger: 'blur' }],
  sort: [{ required: true, message: '请输入排序', trigger: 'change' }]
})
const sceneMap = computed(() => Object.fromEntries(sceneOptions.map((item) => [item.value, item.label])))
const pageMap = computed(() => Object.fromEntries(pageOptions.map((item) => [item.pageKey, item.label])))

const clone = <T,>(value: T): T => JSON.parse(JSON.stringify(value))
const currentPage = () =>
  pageOptions.find((item) => item.pageKey === selectedPageKey.value) || pageOptions[0]
const pageByKey = (pageKey: string) =>
  pageOptions.find((item) => item.pageKey === pageKey) || pageOptions[0]
const sceneLabel = (scene: string) => sceneMap.value[scene] || scene
const pageLabel = (pageKey: string) => pageMap.value[pageKey] || pageKey
const countGroup = (group: AdvancedFilterApi.AdvancedFilterGroup): number =>
  (group?.conditions?.length || 0) + (group?.groups || []).reduce((sum, item) => sum + countGroup(item), 0)
const resetForm = (page = currentPage()) => {
  form.id = undefined
  form.scene = page.scene
  form.pageKey = page.pageKey
  form.name = ''
  form.filter = blank()
  form.sort = templates.value.length * 10 + 10
  form.enabled = true
  form.defaultTemplate = false
  form.version = undefined
}
const updateFilter = (value: AdvancedFilterApi.AdvancedFilterGroup) => {
  form.filter = value
}
const sourceOptions = async (source?: string): Promise<AdvancedFilterApi.AdvancedFilterOption[]> => {
  if (!source) return []
  if (source.startsWith('dict:')) {
    return (await DictDataApi.getDictDataByType(source.slice(5))).map((item) => ({
      value: item.value,
      label: item.label
    }))
  }
  if (source === 'visible-users') {
    return (await UserApi.getSimpleUserList()).map((item) => ({ value: item.id, label: item.nickname }))
  }
  return []
}
const retryOptions = async (fieldKey: string) => {
  const field = fields.value.find((item) => item.fieldKey === fieldKey)
  if (!field?.optionSource) return
  fields.value = fields.value.map((item) =>
    item.fieldKey === fieldKey ? { ...item, optionsLoading: true, optionsError: false } : item
  )
  try {
    const options = await sourceOptions(field.optionSource)
    fields.value = fields.value.map((item) =>
      item.fieldKey === fieldKey ? { ...item, options, optionsLoading: false } : item
    )
  } catch {
    fields.value = fields.value.map((item) =>
      item.fieldKey === fieldKey ? { ...item, optionsLoading: false, optionsError: true } : item
    )
  }
}
const loadCatalog = async () => {
  catalogLoading.value = true
  catalogError.value = false
  try {
    const catalog = await AdvancedFilterApi.getCatalog(query.scene)
    fields.value = catalog.fields.map((field) =>
      field.optionSource && !field.options.length ? { ...field, optionsLoading: true } : field
    )
    await Promise.all(
      catalog.fields.map(async (field) => {
        if (!field.optionSource || field.options.length) return
        try {
          const options = await sourceOptions(field.optionSource)
          fields.value = fields.value.map((item) =>
            item.fieldKey === field.fieldKey ? { ...item, options, optionsLoading: false } : item
          )
        } catch {
          fields.value = fields.value.map((item) =>
            item.fieldKey === field.fieldKey
              ? { ...item, optionsLoading: false, optionsError: true }
              : item
          )
        }
      })
    )
  } catch (loadError: any) {
    catalogError.value = true
    fields.value = []
    error.value = loadError?.msg || loadError?.message || ''
  } finally {
    catalogLoading.value = false
  }
}
const loadTemplates = async () => {
  loading.value = true
  error.value = ''
  try {
    templates.value = await TemplateApi.getSystemTemplateList(query.scene, query.pageKey)
  } catch (loadError: any) {
    templates.value = []
    error.value = loadError?.msg || loadError?.message || '系统预置加载失败'
  } finally {
    loading.value = false
  }
}
const loadAll = async () => {
  await Promise.all([loadCatalog(), loadTemplates()])
}
const changePage = async () => {
  const page = currentPage()
  query.scene = page.scene
  query.pageKey = page.pageKey
  await loadAll()
}
const resetPage = async () => {
  selectedPageKey.value = pageOptions[0].pageKey
  await changePage()
}
const changeFormPage = async () => {
  const page = pageByKey(form.pageKey)
  form.scene = page.scene
  if (page.scene !== query.scene) {
    query.scene = page.scene
    query.pageKey = page.pageKey
    selectedPageKey.value = page.pageKey
    await loadAll()
  }
}
const openCreate = () => {
  resetForm()
  dialogVisible.value = true
}
const openEdit = (row: AdvancedFilterApi.AdvancedFilterTemplate) => {
  form.id = row.id
  form.scene = row.scene
  form.pageKey = row.pageKey
  form.name = row.name
  form.filter = clone(row.filter)
  form.sort = row.sort
  form.enabled = row.enabled
  form.defaultTemplate = row.defaultTemplate
  form.version = row.version
  dialogVisible.value = true
}
const save = async () => {
  await formRef.value?.validate()
  if (!countGroup(form.filter)) {
    message.error('请至少配置一个筛选条件')
    return
  }
  saving.value = true
  try {
    if (form.id) await TemplateApi.updateSystemTemplate(clone(form))
    else await TemplateApi.createSystemTemplate(clone(form))
    message.success('系统预置已保存')
    dialogVisible.value = false
    selectedPageKey.value = form.pageKey
    query.scene = form.scene
    query.pageKey = form.pageKey
    await loadTemplates()
  } finally {
    saving.value = false
  }
}
const remove = async (row: AdvancedFilterApi.AdvancedFilterTemplate) => {
  await ElMessageBox.confirm('删除后该系统预置不再出现在工作台模板列表中，确定删除？', '删除系统预置')
  await TemplateApi.deleteSystemTemplate(row.id)
  message.success('系统预置已删除')
  await loadTemplates()
}

onMounted(loadAll)
</script>

<style scoped>
.template-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.template-heading h3 {
  margin: 0 0 8px;
  font-size: 18px;
}

.template-heading p {
  margin: 0;
  color: var(--el-text-color-secondary);
}

.filter-editor {
  width: 100%;
  min-height: 180px;
}

.catalog-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 160px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 768px) {
  .template-heading {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
