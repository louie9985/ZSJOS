<template>
  <ContentWrap>
    <el-form :model="query" inline @submit.prevent>
      <el-form-item label="任务状态">
        <el-select v-model="query.status" clearable class="!w-180px" placeholder="全部状态">
          <el-option v-for="item in statusOptions" :key="item.value" v-bind="item" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="handleQuery">
          <Icon icon="ep:search" class="mr-5px" />查询
        </el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
      </el-form-item>
    </el-form>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false">
      <template #default
        ><el-button link type="primary" @click="load">重新加载</el-button></template
      >
    </el-alert>
  </ContentWrap>

  <ZsjosAdvancedFilter
    scene="registration"
    placeholder="订单号 / 学员姓名 / 手机号 / 客资编号"
    :keyword="query.keyword || ''"
    @search="(value) => { query.keyword = value; handleQuery() }"
    @change="(value) => { query.advancedFilter = value; handleQuery() }"
  />

  <ContentWrap>
    <el-table v-loading="loading" :data="list" row-key="id" stripe>
      <el-table-column label="订单编号" prop="orderNo" min-width="190" fixed="left" />
      <el-table-column label="学员" prop="studentName" min-width="120" />
      <el-table-column label="手机号" prop="studentMobile" min-width="130" />
      <el-table-column label="客资编号" min-width="210">
        <template #default="{ row }">{{ row.leadNo || '客资编号暂未生成' }}</template>
      </el-table-column>
      <el-table-column label="学业规划师" min-width="130">
        <template #default="{ row }">{{ row.studyPlannerUserName || '待分配' }}</template>
      </el-table-column>
      <el-table-column label="履约状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="报名审核通过" prop="registrationApprovedAt" min-width="170" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.id)">查看</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无报名履约任务" /></template>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load"
    />
  </ContentWrap>

  <el-drawer v-model="detailOpen" title="报名履约详情" size="760px" destroy-on-close>
    <div v-loading="detailLoading">
      <el-alert
        v-if="detailError"
        :title="detailError"
        type="error"
        show-icon
        :closable="false"
        class="mb-16px"
      >
        <template #default
          ><el-button link type="primary" @click="reloadDetail">重试</el-button></template
        >
      </el-alert>
      <template v-else-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单编号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="订单状态">{{
            detail.orderStatusLabel || '未知状态'
          }}</el-descriptions-item>
          <el-descriptions-item label="学员姓名">{{ detail.studentName }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{
            detail.studentMobile || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="客资编号">{{
            detail.leadNo || '客资编号暂未生成'
          }}</el-descriptions-item>
          <el-descriptions-item label="履约状态">
            <el-tag :type="statusTag(detail.status)">{{ statusLabel(detail.status) }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="detail.completionBlockReason"
          :title="detail.completionBlockReason"
          type="warning"
          show-icon
          :closable="false"
          class="mt-16px"
        />

        <div class="section-heading">学员流转</div>
        <div class="checklist">
          <div v-for="route in detail.routes" :key="route.id" class="checklist-item">
            <el-checkbox
              :model-value="route.selected"
              :disabled="!canUpdate || !isEditable(detail.status) || routeSaving"
              @change="(checked) => changeRouteSelection(route, Boolean(checked))"
              >{{ route.departmentName }}</el-checkbox
            >
            <el-select
              v-if="route.selected"
              :model-value="route.assigneeUserId"
              filterable
              :placeholder="`请选择${route.assigneeTypeLabel}`"
              :loading="routeSaving"
              :disabled="!canUpdate || !isEditable(detail.status) || routeSaving"
              @visible-change="(visible) => visible && loadRouteCandidates(route)"
              @change="(value) => changeRouteAssignee(route, Number(value))"
            >
              <el-option
                v-for="candidate in routeCandidates[route.id] ||
                (route.assigneeUserId
                  ? [
                      {
                        id: route.assigneeUserId,
                        nickname: route.assigneeUserName || '已分配负责人'
                      }
                    ]
                  : [])"
                :key="candidate.id"
                :label="candidate.nickname"
                :value="candidate.id"
              />
            </el-select>
            <span v-else class="checklist-meta">未选择</span>
          </div>
        </div>

        <div class="section-heading">履约清单</div>
        <el-alert
          v-if="!canUpdate && isEditable(detail.status)"
          title="当前账号仅可查看，不能更新履约清单"
          type="info"
          show-icon
          :closable="false"
          class="mb-12px"
        />
        <div class="checklist">
          <div v-for="item in detail.items" :key="item.id" class="checklist-item">
            <div>
              <strong>{{ item.title }}</strong>
              <div v-if="item.checked" class="checklist-meta">
                {{ item.checkedByUserName || '已完成'
                }}<span v-if="item.checkedAt"> · {{ item.checkedAt }}</span>
              </div>
            </div>
            <el-switch
              v-if="item.itemType === 'checkbox'"
              :model-value="item.checked"
              :loading="savingItemId === item.id"
              :disabled="!canUpdate || !isEditable(detail.status) || savingItemId === item.id"
              @change="(checked) => changeItem(item, Boolean(checked))"
            />
            <div v-else-if="item.itemType === 'attachment'" class="attachment-actions">
              <div v-for="attachment in item.attachments" :key="attachment.id"
                ><el-link :href="attachment.fileUrl" target="_blank" type="primary">{{
                  attachment.originalName
                }}</el-link
                ><el-button
                  link
                  type="danger"
                  :disabled="savingItemId === item.id"
                  @click="deleteAttachment(item.id, attachment.id)"
                  >删除</el-button
                ></div
              >
              <el-upload
                :show-file-list="false"
                :http-request="(options) => uploadAttachment(item, options.file as File)"
                accept=".jpg,.jpeg,.png,.webp,.pdf,.doc,.docx,.xls,.xlsx"
                :disabled="
                  !canUpdate ||
                  !isEditable(detail.status) ||
                  savingItemId === item.id ||
                  (item.attachments?.length || 0) >= 9
                "
                ><el-button :loading="savingItemId === item.id">上传附件</el-button></el-upload
              >
            </div>
            <el-tag v-else type="info">系统固定项</el-tag>
          </div>
        </div>
      </template>
    </div>
    <template #footer>
      <el-button @click="detailOpen = false">关闭</el-button>
      <el-button
        v-if="canComplete && detail && isEditable(detail.status)"
        type="primary"
        :loading="completing"
        :disabled="!detail.completable"
        @click="complete"
        >完成报名履约</el-button
      >
    </template>
  </el-drawer>
</template>

<script lang="ts" setup>
import * as RegistrationApi from '@/api/zsjos/registration'
import { useUserStore } from '@/store/modules/user'
import { useMessage } from '@/hooks/web/useMessage'
import ZsjosAdvancedFilter from './components/ZsjosAdvancedFilter.vue'

defineOptions({ name: 'ZsjosRegistrationPool' })

const message = useMessage()
const userStore = useUserStore()
const loading = ref(false)
const error = ref('')
const list = ref<RegistrationApi.RegistrationCase[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, status: undefined as string | undefined, keyword: '', advancedFilter: undefined as any })
const detailOpen = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detail = ref<RegistrationApi.RegistrationCase>()
const detailId = ref<number>()
const routeCandidates = ref<Record<number, RegistrationApi.StudyPlanner[]>>({})
const routeSaving = ref(false)
const savingItemId = ref<number>()
const completing = ref(false)

const statusOptions = [
  { value: 'pending', label: '待办理' },
  { value: 'processing', label: '办理中' },
  { value: 'completed', label: '已完成' },
  { value: 'cancelled', label: '已取消' }
]
const hasPermission = (permission: string) =>
  userStore.getPermissions.has('*:*:*') || userStore.getPermissions.has(permission)
const canUpdate = computed(() => hasPermission('zsjos:registration:update'))
const canComplete = computed(() => hasPermission('zsjos:registration:complete'))
const statusLabel = (status: string) =>
  statusOptions.find((item) => item.value === status)?.label || '未知状态'
const isEditable = (status: string) => status === 'pending' || status === 'processing'
const statusTag = (status: string): 'success' | 'info' | 'warning' =>
  status === 'completed' ? 'success' : status === 'cancelled' ? 'info' : 'warning'

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const data = await RegistrationApi.getRegistrationPoolPage(query)
    list.value = data.list
    total.value = data.total
  } catch (cause: any) {
    list.value = []
    total.value = 0
    error.value = cause?.msg || cause?.message || '报名履约任务加载失败'
  } finally {
    loading.value = false
  }
}
const handleQuery = () => {
  query.pageNo = 1
  void load()
}
const resetQuery = () => {
  query.pageNo = 1
  query.status = undefined
  query.keyword = ''
  query.advancedFilter = undefined
  void load()
}
const openDetail = async (id: number) => {
  detailId.value = id
  detailOpen.value = true
  detailLoading.value = true
  detailError.value = ''
  try {
    detail.value = await RegistrationApi.getRegistrationCase(id)
  } catch (cause: any) {
    detail.value = undefined
    detailError.value = cause?.msg || cause?.message || '报名履约详情加载失败'
  } finally {
    detailLoading.value = false
  }
}
const reloadDetail = () => detailId.value && openDetail(detailId.value)
const refreshDetailAndList = async (id: number) => {
  detail.value = await RegistrationApi.getRegistrationCase(id)
  await load()
}
const changeItem = async (item: RegistrationApi.RegistrationChecklistItem, checked: boolean) => {
  if (!detail.value) return
  savingItemId.value = item.id
  const id = detail.value.id
  const previous = detail.value
  detail.value = {
    ...previous,
    status: 'processing',
    statusLabel: '办理中',
    items: previous.items.map((current) =>
      current.id === item.id ? { ...current, checked } : current
    )
  }
  try {
    detail.value = await RegistrationApi.updateRegistrationItem(
      id,
      item.id,
      checked,
      previous.version
    )
    void load()
  } catch (cause: any) {
    detail.value = previous
    message.error(cause?.msg || cause?.message || '履约事项更新失败')
    if ((cause?.msg || cause?.message || '').includes('其他人员修改')) await openDetail(id)
  } finally {
    savingItemId.value = undefined
  }
}
const loadRouteCandidates = async (route: RegistrationApi.RegistrationRoute) => {
  if (!detail.value || routeCandidates.value[route.id]) return
  try {
    routeCandidates.value[route.id] = await RegistrationApi.getRouteCandidates(
      detail.value.id,
      route.id
    )
  } catch (cause: any) {
    message.error(cause?.msg || cause?.message || '负责人加载失败')
  }
}
const saveRoutes = async (
  routes: RegistrationApi.RegistrationRoute[],
  previous: RegistrationApi.RegistrationCase
) => {
  routeSaving.value = true
  detail.value = { ...previous, routes }
  try {
    detail.value = await RegistrationApi.updateRegistrationRoutes(
      previous.id,
      routes.map((route) => ({
        routeId: route.id,
        selected: route.selected,
        assigneeUserId: route.assigneeUserId
      })),
      previous.version
    )
    void load()
  } catch (cause: any) {
    detail.value = previous
    message.error(cause?.msg || cause?.message || '流转配置更新失败')
    if ((cause?.msg || cause?.message || '').includes('其他人员修改')) await openDetail(previous.id)
  } finally {
    routeSaving.value = false
  }
}
const changeRouteSelection = async (
  route: RegistrationApi.RegistrationRoute,
  selected: boolean
) => {
  if (!detail.value) return
  const previous = detail.value
  const routes = previous.routes.map((item) =>
    item.id === route.id
      ? { ...item, selected, assigneeUserId: selected ? item.assigneeUserId : undefined }
      : item
  )
  if (selected) {
    detail.value = { ...previous, routes }
    await loadRouteCandidates(route)
    return
  }
  await saveRoutes(routes, previous)
}
const changeRouteAssignee = async (
  route: RegistrationApi.RegistrationRoute,
  assigneeUserId: number
) => {
  if (!detail.value) return
  await saveRoutes(
    detail.value.routes.map((item) =>
      item.id === route.id ? { ...item, selected: true, assigneeUserId } : item
    ),
    detail.value
  )
}
const uploadAttachment = async (item: RegistrationApi.RegistrationChecklistItem, file: File) => {
  if (!detail.value) return
  savingItemId.value = item.id
  try {
    await RegistrationApi.uploadRegistrationAttachment(
      detail.value.id,
      item.id,
      file,
      detail.value.version
    )
    await openDetail(detail.value.id)
  } catch (cause: any) {
    message.error(cause?.msg || cause?.message || '附件上传失败')
  } finally {
    savingItemId.value = undefined
  }
}
const deleteAttachment = async (itemId: number, attachmentId: number) => {
  if (!detail.value) return
  savingItemId.value = itemId
  try {
    detail.value = await RegistrationApi.deleteRegistrationAttachment(
      detail.value.id,
      itemId,
      attachmentId,
      detail.value.version
    )
  } catch (cause: any) {
    message.error(cause?.msg || cause?.message || '附件删除失败')
  } finally {
    savingItemId.value = undefined
  }
}
const complete = async () => {
  if (!detail.value) return
  try {
    await message.confirm('完成后将生成学员服务关系，确认继续？')
  } catch {
    return
  }
  completing.value = true
  const id = detail.value.id
  try {
    await RegistrationApi.completeRegistration(id, detail.value.version)
    message.success('报名履约已完成')
    await refreshDetailAndList(id)
  } catch (cause: any) {
    message.error(cause?.msg || cause?.message || '报名履约完成失败')
    await openDetail(id)
  } finally {
    completing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.section-heading {
  margin: 24px 0 12px;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.checklist {
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.checklist-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 64px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.checklist-item:last-child {
  border-bottom: 0;
}

.checklist-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.attachment-actions {
  display: flex;
  align-items: flex-end;
  flex-direction: column;
  gap: 6px;
}
</style>
