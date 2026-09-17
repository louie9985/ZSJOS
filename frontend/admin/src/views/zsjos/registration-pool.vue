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

        <div class="section-heading">{{ detail.assignmentMode === 'class_per_item' ? '逐商品分班' : '历史学习规划师分配' }}</div>
        <div v-if="detail.assignmentMode === 'class_per_item'" class="checklist">
          <el-alert v-if="classOptionsError" type="error" :closable="false" title="班级加载失败" :description="classOptionsError" show-icon />
          <el-button v-if="classOptionsError" :loading="classOptionsLoading" @click="loadClassOptions">重试加载班级</el-button>
          <el-alert v-if="classesDirty" type="info" :closable="false" title="分班选择尚未保存，请先保存分班再完成履约" />
          <div v-for="assignment in detail.classAssignments" :key="assignment.orderItemId" class="checklist-item">
            <div><strong>{{ assignment.productName || '历史产品信息缺失' }}</strong><ProductSpecs :product="assignment" /><div class="checklist-meta">{{ assignment.categoryName || '产品分类' }}<span v-if="assignment.errorReason"> · {{ assignment.errorReason }}</span></div></div>
            <el-select v-model="classDraft[assignment.orderItemId]" class="!w-320px" filterable placeholder="选择班级（含待分班）" :disabled="!canUpdate || !isEditable(detail.status) || classSaving" :loading="classOptionsLoading" :no-data-text="classOptionsError ? '班级加载失败，请重试' : '暂无未结课班级，请联系班级管理员'" @visible-change="(visible) => visible && loadClassOptions()">
              <el-option v-for="option in classOptions" :key="option.id" :value="option.id" :label="option.systemClass ? '待分班' : `${option.className} · ${option.homeroomUserName || '未配置班主任'}`" />
              <el-option v-if="assignment.classId && !classOptions.some(option => option.id === assignment.classId)" :value="assignment.classId" :label="assignment.className || '已选班级'" disabled />
            </el-select>
          </div>
          <el-button v-if="canUpdate && isEditable(detail.status)" type="primary" :loading="classSaving" @click="saveClasses">保存分班</el-button>
        </div>
        <div class="checklist">
          <div v-for="route in detail.assignmentMode === 'class_per_item' ? [] : detail.routes" :key="route.id" class="checklist-item">
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
              <ClipboardUploadActions :disabled="!canUpdate || !isEditable(detail.status) || savingItemId === item.id || (item.attachments?.length || 0) >= 9" :can-paste="canUpdate && isEditable(detail.status) && savingItemId !== item.id && (item.attachments?.length || 0) < 9" @files="files => pasteAttachment(item, files)"><el-upload
                :show-file-list="false"
                :http-request="(options) => uploadAttachment(item, options.file as File)"
                accept=".jpg,.jpeg,.png,.webp,.pdf,.doc,.docx,.xls,.xlsx"
                :disabled="
                  !canUpdate ||
                  !isEditable(detail.status) ||
                  savingItemId === item.id ||
                  (item.attachments?.length || 0) >= 9
                "
                ><el-button :loading="savingItemId === item.id">上传附件</el-button></el-upload></ClipboardUploadActions
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
        :disabled="!detail.completable || classesDirty || classSaving"
        @click="complete"
        >完成报名履约</el-button
      >
    </template>
  </el-drawer>
</template>

<script lang="ts" setup>
import ProductSpecs from './components/ProductSpecs.vue'
import * as RegistrationApi from '@/api/zsjos/registration'
import * as DeliveryClassApi from '@/api/zsjos/deliveryClass'
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
const classSaving = ref(false)
const classDraft = reactive<Record<number, number | undefined>>({})
const classOptions = ref<DeliveryClassApi.DeliveryClassOption[]>([])
const classOptionsLoading = ref(false)
const classOptionsError = ref('')
const classesDirty = computed(() => detail.value?.assignmentMode === 'class_per_item' && detail.value.classAssignments?.some(item => (classDraft[item.orderItemId] ?? null) !== (item.classId ?? null)))
const savingItemId = ref<number>()
const completing = ref(false)
import ClipboardUploadActions from '@/components/UploadFile/src/ClipboardUploadActions.vue'
const pasteAttachment = (item: RegistrationApi.RegistrationChecklistItem, files: File[]) => { const file = files[0]; if (file) void uploadAttachment(item, file) }

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
    if (detail.value.assignmentMode === 'class_per_item') for (const item of detail.value.classAssignments || []) classDraft[item.orderItemId] = item.classId
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
const loadClassOptions = async () => {
  if (classOptionsLoading.value) return
  classOptionsLoading.value = true
  classOptionsError.value = ''
  try {
    classOptions.value = await DeliveryClassApi.getDeliveryClassOptions()
  } catch (cause: any) {
    classOptions.value = []
    classOptionsError.value = cause?.msg || cause?.message || '可选班级加载失败'
  } finally {
    classOptionsLoading.value = false
  }
}
const saveClasses = async () => {
  if (!detail.value?.classAssignments) return
  const assignments = detail.value.classAssignments.map((item) => ({ orderItemId: item.orderItemId, classId: classDraft[item.orderItemId] }))
  if (assignments.some((item) => !item.classId)) { message.warning('请为每个订单商品选择班级'); return }
  classSaving.value = true
  try { detail.value = await RegistrationApi.updateRegistrationClassAssignments(detail.value.id, assignments as Array<{ orderItemId: number; classId: number }>, detail.value.version); message.success('分班已保存'); await load() }
  catch (cause: any) { message.error(cause?.msg || cause?.message || '分班保存失败'); await openDetail(detail.value.id) }
  finally { classSaving.value = false }
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
