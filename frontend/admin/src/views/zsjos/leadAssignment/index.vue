<template>
  <ContentWrap v-if="isAdminMode" class="scene-header">
    <el-button text @click="router.push('/system/user-relation')">
      <Icon icon="ep:arrow-left" class="mr-5px" />返回场景管理
    </el-button>
    <el-divider direction="vertical" />
    <div>
      <strong>{{ scene?.name || '用户关系数据' }}</strong>
      <span class="scene-code">{{ sceneCode }}</span>
    </div>
  </ContentWrap>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="queryParams" :inline="true" class="-mb-15px">
      <el-form-item :label="sourceLabel" prop="keyword">
        <el-input
          v-model="queryParams.keyword"
          placeholder="姓名或手机号"
          clearable
          class="!w-220px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="所属部门" prop="deptId">
        <el-tree-select
          v-model="queryParams.deptId"
          :data="deptTree"
          :props="defaultProps"
          check-strictly
          node-key="id"
          clearable
          class="!w-220px"
          placeholder="全部部门"
        />
      </el-form-item>
      <el-form-item label="配置状态" prop="configured">
        <el-select v-model="queryParams.configured" clearable class="!w-180px" placeholder="全部">
          <el-option label="已配置" :value="true" />
          <el-option label="未配置" :value="false" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button
          type="primary"
          plain
          :disabled="checkedRows.length === 0"
          v-hasPermi="[updatePermission]"
          @click="openBatchDrawer"
        >
          <Icon icon="ep:operation" class="mr-5px" />批量配置
        </el-button>
        <el-button plain v-hasPermi="[logPermission]" @click="openLogDialog">
          <Icon icon="ep:document" class="mr-5px" />变更记录
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" @selection-change="checkedRows = $event">
      <el-table-column type="selection" width="48" />
      <el-table-column :label="sourceLabel" min-width="210">
        <template #default="{ row }">
          <div class="person-cell">
            <el-avatar :size="34" :src="row.avatar">{{ row.nickname?.slice(0, 1) }}</el-avatar>
            <div>
              <div class="person-name">{{ row.nickname }}</div>
              <div class="person-meta">{{ row.maskedMobile || '未填写手机号' }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="所属部门" prop="deptName" min-width="140" />
      <el-table-column :label="`已绑定${targetLabel}`" min-width="300">
        <template #default="{ row }">
          <div v-if="row.salesUsers.length" class="sales-tags">
            <el-tag v-for="sales in row.salesUsers.slice(0, 3)" :key="sales.id" effect="plain">
              {{ sales.nickname }}
            </el-tag>
            <el-tag v-if="row.salesUsers.length > 3" type="info" effect="plain">
              +{{ row.salesUsers.length - 3 }}
            </el-tag>
          </div>
          <span v-else class="empty-text">尚未配置</span>
        </template>
      </el-table-column>
      <el-table-column label="有效/异常" width="110" align="center">
        <template #default="{ row }">
          <span class="valid-count">{{ row.validSalesCount }}</span>
          <span> / </span>
          <span :class="{ 'invalid-count': row.invalidSalesCount > 0 }">{{
            row.invalidSalesCount
          }}</span>
        </template>
      </el-table-column>
      <el-table-column label="账号状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 0 ? 'success' : 'info'">
            {{ row.status === 0 ? '正常' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="最后修改"
        prop="updateTime"
        width="180"
        :formatter="zsjosDateFormatter"
      />
      <el-table-column label="操作" width="90" fixed="right" align="center">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            v-hasPermi="[updatePermission]"
            @click="openSingleDrawer(row)"
            >配置</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <el-drawer v-model="drawerVisible" :title="drawerTitle" size="780px" destroy-on-close>
    <div class="drawer-source">
      <span class="drawer-source-label">配置对象</span>
      <strong>{{ selectedSourceLabel }}</strong>
    </div>
    <el-radio-group v-if="isBatch" v-model="saveMode" class="mode-switch">
      <el-radio-button value="append">追加绑定</el-radio-button>
      <el-radio-button value="replace">替换原绑定</el-radio-button>
      <el-radio-button value="remove">解除指定绑定</el-radio-button>
    </el-radio-group>
    <el-alert
      v-if="saveMode === 'replace' && isBatch"
      title="将以本次选择替换所选员工的原有绑定"
      type="warning"
      show-icon
      :closable="false"
      class="mb-16px"
    />
    <div class="assignment-picker">
      <section class="candidate-pane">
        <div class="pane-header">
          <strong>可选{{ targetLabel }}</strong>
          <span>{{ filteredSales.length }} 人</span>
        </div>
        <el-input v-model="salesKeyword" clearable placeholder="搜索姓名、手机号或部门">
          <template #prefix><Icon icon="ep:search" /></template>
        </el-input>
        <el-checkbox-group v-model="selectedSalesIds" class="candidate-list">
          <el-checkbox
            v-for="sales in filteredSales"
            :key="sales.id"
            :value="sales.id"
            class="candidate-row"
          >
            <div class="candidate-content">
              <span class="person-name">{{ sales.nickname }}</span>
              <span class="person-meta">
                {{ sales.maskedMobile || '未填写手机号' }} · {{ sales.deptName || '未分配部门' }}
              </span>
            </div>
          </el-checkbox>
        </el-checkbox-group>
        <el-empty
          v-if="filteredSales.length === 0"
          :description="`没有符合条件的${targetLabel}账号`"
          :image-size="72"
        />
      </section>
      <section class="selected-pane">
        <div class="pane-header">
          <strong>已选择</strong>
          <el-tag type="primary">{{ selectedSalesIds.length }} 人</el-tag>
        </div>
        <div class="selected-list">
          <div v-for="sales in selectedSales" :key="sales.id" class="selected-row">
            <div>
              <div class="person-name">{{ sales.nickname }}</div>
              <div class="person-meta">{{ sales.deptName || '未分配部门' }}</div>
            </div>
            <el-button link type="danger" @click="removeSelected(sales.id)">
              <Icon icon="ep:close" />
            </el-button>
          </div>
          <el-empty
            v-if="selectedSales.length === 0"
            :description="`暂未选择${targetLabel}`"
            :image-size="72"
          />
        </div>
      </section>
    </div>
    <template #footer>
      <el-button @click="drawerVisible = false">取消</el-button>
      <ZsjosPopconfirm
        :action="confirmAction"
        :danger="saveMode === 'remove'"
        v-model:visible="confirmVisible"
        @confirm="submitRelations"
      >
        <el-button
          :type="saveMode === 'remove' ? 'danger' : 'primary'"
          :loading="saving"
          @click="prepareSubmit"
          >保存配置</el-button
        >
      </ZsjosPopconfirm>
    </template>
  </el-drawer>

  <el-dialog v-model="logVisible" :title="`${sceneName}变更记录`" width="900px">
    <el-table v-loading="logLoading" :data="logList">
      <el-table-column
        label="操作时间"
        prop="createTime"
        width="180"
        :formatter="zsjosDateFormatter"
      />
      <el-table-column label="操作人" prop="operatorName" width="120" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">{{ actionLabels[row.actionType] }}</template>
      </el-table-column>
      <el-table-column
        :label="sourceLabel"
        prop="sourceUsers"
        min-width="180"
        show-overflow-tooltip
      />
      <el-table-column
        :label="targetLabel"
        prop="targetUsers"
        min-width="220"
        show-overflow-tooltip
      />
    </el-table>
    <Pagination
      :total="logTotal"
      v-model:page="logQuery.pageNo"
      v-model:limit="logQuery.pageSize"
      @pagination="getLogs"
    />
  </el-dialog>
</template>

<script lang="ts" setup>
import { zsjosDateFormatter } from '@/utils/zsjosTime'
import { defaultProps, handleTree } from '@/utils/tree'
import * as DeptApi from '@/api/system/dept'
import * as AssignmentApi from '@/api/zsjos/leadAssignment'
import * as UserRelationApi from '@/api/zsjos/userRelation'
import ZsjosPopconfirm from '../components/ZsjosPopconfirm.vue'
import { relationConfirmAction } from '../components/irreversibleConfirm'

defineOptions({ name: 'ZsjosLeadAssignment' })

const message = useMessage()
const route = useRoute()
const router = useRouter()
const isAdminMode = computed(() => route.name === 'ZsjosUserRelationData')
const sceneCode = computed(() => String(route.params.sceneCode || 'lead_specified_assignment'))
const scene = ref<UserRelationApi.UserRelationSceneVO>()
const sceneName = computed(() => scene.value?.name || '派单关系')
const sourceLabel = computed(() => scene.value?.sourceLabel || '派单员工')
const targetLabel = computed(() => scene.value?.targetLabel || '销售专员')
const updatePermission = computed(() =>
  isAdminMode.value ? 'zsjos:user-relation:update' : 'zsjos:lead-assignment:update'
)
const logPermission = computed(() =>
  isAdminMode.value ? 'zsjos:user-relation:log-query' : 'zsjos:lead-assignment:log-query'
)
const loading = ref(false)
const saving = ref(false)
const confirmVisible = ref(false)
const total = ref(0)
const list = ref<AssignmentApi.AssignmentRelationVO[]>([])
const checkedRows = ref<AssignmentApi.AssignmentRelationVO[]>([])
const deptTree = ref<any[]>([])
const queryFormRef = ref()
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  keyword: '',
  deptId: undefined,
  configured: undefined
})

const drawerVisible = ref(false)
const isBatch = ref(false)
const activeSource = ref<AssignmentApi.AssignmentRelationVO>()
const eligibleSales = ref<AssignmentApi.AssignmentUserVO[]>([])
const selectedSalesIds = ref<number[]>([])
const salesKeyword = ref('')
const saveMode = ref<'append' | 'replace' | 'remove'>('replace')

const logVisible = ref(false)
const logLoading = ref(false)
const logList = ref<AssignmentApi.AssignmentLogVO[]>([])
const logTotal = ref(0)
const logQuery = reactive({ pageNo: 1, pageSize: 10 })
const actionLabels = { append: '追加绑定', replace: '替换绑定', remove: '解除绑定' }

const drawerTitle = computed(() =>
  isBatch.value ? `批量配置${sceneName.value}` : `配置可选${targetLabel.value}`
)
const selectedSourceLabel = computed(() =>
  isBatch.value
    ? `已选择 ${checkedRows.value.length} 名${sourceLabel.value}`
    : `${activeSource.value?.nickname || ''}${activeSource.value?.deptName ? ` · ${activeSource.value.deptName}` : ''}`
)
const filteredSales = computed(() => {
  const keyword = salesKeyword.value.trim().toLowerCase()
  if (!keyword) return eligibleSales.value
  return eligibleSales.value.filter((sales) =>
    [sales.nickname, sales.maskedMobile, sales.deptName].some((value) =>
      value?.toLowerCase().includes(keyword)
    )
  )
})
const selectedSales = computed(() =>
  eligibleSales.value.filter((sales) => selectedSalesIds.value.includes(sales.id))
)
const confirmAction = computed(() =>
  relationConfirmAction(
    saveMode.value,
    isAdminMode.value ? sceneName.value : '派单关系',
    isBatch.value
      ? { batchCount: checkedRows.value.length }
      : { name: activeSource.value?.nickname || '当前员工' }
  )
)

const getList = async () => {
  loading.value = true
  try {
    if (isAdminMode.value) {
      const data = await UserRelationApi.getRelationPage({
        ...queryParams,
        sceneCode: sceneCode.value
      })
      list.value = data.list.map((item) => ({
        ...item,
        salesUsers: item.targetUsers,
        validSalesCount: item.validTargetCount,
        invalidSalesCount: item.invalidTargetCount
      }))
      total.value = data.total
      return
    }
    const data = await AssignmentApi.getRelationPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}

const ensureSalesLoaded = async () => {
  if (eligibleSales.value.length > 0) return
  eligibleSales.value = isAdminMode.value
    ? await UserRelationApi.getEligibleTargets(sceneCode.value)
    : await AssignmentApi.getEligibleSales()
}

const openSingleDrawer = async (row: AssignmentApi.AssignmentRelationVO) => {
  await ensureSalesLoaded()
  isBatch.value = false
  activeSource.value = row
  saveMode.value = 'replace'
  selectedSalesIds.value = row.salesUsers.map((sales) => sales.id)
  salesKeyword.value = ''
  drawerVisible.value = true
}

const openBatchDrawer = async () => {
  await ensureSalesLoaded()
  isBatch.value = true
  activeSource.value = undefined
  saveMode.value = 'append'
  selectedSalesIds.value = []
  salesKeyword.value = ''
  drawerVisible.value = true
}

const removeSelected = (id: number) => {
  selectedSalesIds.value = selectedSalesIds.value.filter((value) => value !== id)
}

const submitRelations = async () => {
  confirmVisible.value = false
  if (selectedSalesIds.value.length === 0 && saveMode.value !== 'replace') {
    message.warning(`请至少选择一名${targetLabel.value}`)
    return
  }
  saving.value = true
  try {
    const sourceUserIds = isBatch.value
      ? checkedRows.value.map((row) => row.id)
      : [activeSource.value!.id]
    if (isAdminMode.value) {
      await UserRelationApi.saveRelations({
        sceneCode: sceneCode.value,
        sourceUserIds,
        targetUserIds: selectedSalesIds.value,
        mode: saveMode.value
      })
    } else {
      await AssignmentApi.saveRelations({
        sourceUserIds,
        targetUserIds: selectedSalesIds.value,
        mode: saveMode.value
      })
    }
    message.success(`${sceneName.value}已更新`)
    drawerVisible.value = false
    checkedRows.value = []
    await getList()
  } finally {
    saving.value = false
  }
}

const prepareSubmit = () => {
  if (selectedSalesIds.value.length === 0 && saveMode.value !== 'replace') {
    message.warning(`请至少选择一名${targetLabel.value}`)
    return
  }
  confirmVisible.value = true
}

const getLogs = async () => {
  logLoading.value = true
  try {
    const data = isAdminMode.value
      ? await UserRelationApi.getLogPage({ ...logQuery, scene: sceneCode.value })
      : await AssignmentApi.getLogPage(logQuery)
    logList.value = data.list
    logTotal.value = data.total
  } finally {
    logLoading.value = false
  }
}

const openLogDialog = () => {
  logVisible.value = true
  logQuery.pageNo = 1
  getLogs()
}

onMounted(async () => {
  if (isAdminMode.value) {
    const scenes = await UserRelationApi.getSceneSimpleList()
    scene.value = scenes.find((item) => item.code === sceneCode.value)
    if (!scene.value) {
      message.error('用户关系场景不存在')
      await router.push('/system/user-relation')
      return
    }
  }
  const depts = await DeptApi.getSimpleDeptList()
  deptTree.value = handleTree(depts)
  await getList()
})
</script>

<style scoped>
.scene-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.scene-code {
  margin-left: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.person-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.person-name {
  font-weight: 600;
  line-height: 20px;
  color: var(--el-text-color-primary);
}

.person-meta {
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}

.sales-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.empty-text {
  color: var(--el-text-color-placeholder);
}

.valid-count {
  font-weight: 600;
  color: var(--el-color-success);
}

.invalid-count {
  font-weight: 600;
  color: var(--el-color-danger);
}

.drawer-source {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  margin-bottom: 16px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}

.drawer-source-label {
  color: var(--el-text-color-secondary);
}

.mode-switch {
  margin-bottom: 16px;
}

.assignment-picker {
  display: grid;
  min-height: 460px;
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  grid-template-columns: minmax(0, 1fr) minmax(260px, 0.72fr);
}

.candidate-pane,
.selected-pane {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
}

.selected-pane {
  background: var(--el-fill-color-extra-light);
  border-left: 1px solid var(--el-border-color);
}

.pane-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--el-text-color-secondary);
}

.candidate-list,
.selected-list {
  max-height: 390px;
  min-height: 0;
  overflow-y: auto;
}

.candidate-row {
  display: flex;
  width: 100%;
  height: auto;
  padding: 10px 6px;
  margin: 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.candidate-row :deep(.el-checkbox__label) {
  min-width: 0;
  flex: 1;
}

.candidate-content {
  display: flex;
  flex-direction: column;
}

.selected-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

@media (width <= 768px) {
  .assignment-picker {
    grid-template-columns: 1fr;
  }

  .selected-pane {
    border-top: 1px solid var(--el-border-color);
    border-left: 0;
  }
}
</style>
