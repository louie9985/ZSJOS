<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="queryParams" inline label-width="76px">
      <el-form-item label="客资状态" prop="status">
        <el-select v-model="queryParams.status" clearable class="!w-150px">
          <el-option v-for="item in LeadApi.LEAD_STATUS_OPTIONS" :key="item.value" v-bind="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="分配状态" prop="assignmentStatus">
        <el-select v-model="queryParams.assignmentStatus" clearable class="!w-150px">
          <el-option
            v-for="item in LeadApi.ASSIGNMENT_STATUS_OPTIONS"
            :key="item.value"
            v-bind="item"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="来源渠道" prop="sourceChannel">
        <el-select v-model="queryParams.sourceChannel" clearable class="!w-170px">
          <el-option
            v-for="item in zsjosLeadSourceChannel"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="客资分类" prop="leadCategory">
        <el-select v-model="queryParams.leadCategory" clearable class="!w-170px">
          <el-option
            v-for="item in zsjosLeadCategory"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="提交人" prop="sourceUserId">
        <el-select v-model="queryParams.sourceUserId" filterable clearable class="!w-170px">
          <el-option v-for="user in users" :key="user.id" :label="user.nickname" :value="user.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="负责人" prop="ownerUserId">
        <el-select v-model="queryParams.ownerUserId" filterable clearable class="!w-170px">
          <el-option v-for="user in users" :key="user.id" :label="user.nickname" :value="user.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="提交时间" prop="submittedAt">
        <el-date-picker
          v-model="queryParams.submittedAt"
          type="datetimerange"
          value-format="YYYY-MM-DD HH:mm:ss"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          class="!w-360px"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleQuery"><Icon icon="ep:search" />查询</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" />重置</el-button>
      </el-form-item>
    </el-form>
    <ZsjosAdvancedFilter scene="lead" placeholder="姓名 / 手机号 / 微信号" :keyword="queryParams.keyword" @search="handleAdvancedSearch" @change="handleAdvancedFilter" />
  </ContentWrap>

  <ContentWrap>
    <el-alert v-if="error" type="error" :title="error" show-icon :closable="false" class="mb-16px">
      <template #default
        ><el-button link type="primary" @click="getList">重新加载</el-button></template
      >
    </el-alert>
    <el-table v-loading="loading" :data="list" stripe :show-overflow-tooltip="true">
      <el-table-column label="客资编号" prop="id" width="100" fixed="left" />
      <el-table-column label="客户" min-width="120" fixed="left">
        <template #default="scope">
          <el-button link type="primary" @click="openDetail(scope.row)">{{
            scope.row.submittedName
          }}</el-button>
        </template>
      </el-table-column>
      <el-table-column label="手机号" prop="submittedMobile" min-width="130" />
      <el-table-column label="微信号" prop="submittedWechatId" min-width="140" />
      <el-table-column label="地区" min-width="130">
        <template #default="scope">{{ areaText(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="客资分类" min-width="120">
        <template #default="scope">{{
          dictLabel(zsjosLeadCategory, scope.row.leadCategory)
        }}</template>
      </el-table-column>
      <el-table-column label="来源渠道" min-width="120">
        <template #default="scope">{{
          dictLabel(zsjosLeadSourceChannel, scope.row.sourceChannel)
        }}</template>
      </el-table-column>
      <el-table-column label="主意向产品" min-width="180">
        <template #default="scope">{{ productText(scope.row.primaryProduct) }}</template>
      </el-table-column>
      <el-table-column label="客资状态" width="100">
        <template #default="scope"
          ><el-tag>{{ leadStatusLabel(scope.row.status) }}</el-tag></template
        >
      </el-table-column>
      <el-table-column label="分配状态" width="100">
        <template #default="scope"
          ><el-tag type="info">{{
            assignmentStatusLabel(scope.row.assignmentStatus)
          }}</el-tag></template
        >
      </el-table-column>
      <el-table-column label="提交人" min-width="110">
        <template #default="scope">{{
          userText(scope.row.sourceUserId, scope.row.sourceUserName)
        }}</template>
      </el-table-column>
      <el-table-column label="负责人" min-width="110">
        <template #default="scope">{{
          userText(scope.row.ownerUserId, scope.row.ownerUserName)
        }}</template>
      </el-table-column>
      <el-table-column label="提交时间" min-width="165">
        <template #default="scope">{{ formatZsjosTimestamp(scope.row.submittedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="80" fixed="right">
        <template #default="scope"
          ><el-button link type="primary" @click="openDetail(scope.row)">详情</el-button></template
        >
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <el-drawer v-model="detailVisible" title="客资详情" size="720px" destroy-on-close>
    <div v-loading="detailLoading">
      <el-alert v-if="detailError" type="error" :title="detailError" show-icon :closable="false">
        <template #default
          ><el-button link type="primary" @click="reloadDetail">重新加载</el-button></template
        >
      </el-alert>
      <template v-else-if="detail">
        <el-descriptions title="客户资料" :column="2" border>
          <el-descriptions-item label="客户姓名">{{ detail.submittedName }}</el-descriptions-item>
          <el-descriptions-item label="客资编号">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{
            detail.submittedMobile || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="微信号">{{
            detail.submittedWechatId || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="所在地区" :span="2">{{
            areaText(detail)
          }}</el-descriptions-item>
        </el-descriptions>
        <el-descriptions title="客资信息" :column="2" border class="mt-20px">
          <el-descriptions-item label="客资分类">{{
            dictLabel(zsjosLeadCategory, detail.leadCategory)
          }}</el-descriptions-item>
          <el-descriptions-item label="来源渠道">{{
            dictLabel(zsjosLeadSourceChannel, detail.sourceChannel)
          }}</el-descriptions-item>
          <el-descriptions-item label="客资状态">{{
            leadStatusLabel(detail.status)
          }}</el-descriptions-item>
          <el-descriptions-item label="分配状态">{{
            assignmentStatusLabel(detail.assignmentStatus)
          }}</el-descriptions-item>
          <el-descriptions-item label="提交备注" :span="2">{{
            detail.remark || '-'
          }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.closeReason" label="关闭原因" :span="2">{{
            detail.closeReason
          }}</el-descriptions-item>
        </el-descriptions>
        <div class="detail-heading">意向产品</div>
        <el-table
          :data="detail.intendedProducts || []"
          size="small"
          border
          empty-text="暂无意向产品"
        >
          <el-table-column label="主意向" width="80"
            ><template #default="scope"
              ><el-tag v-if="scope.row.primary" type="success">是</el-tag></template
            ></el-table-column
          >
          <el-table-column label="课程" min-width="150"
            ><template #default="scope">{{
              scope.row.spuName || '未明确'
            }}</template></el-table-column
          >
          <el-table-column label="具体方案" min-width="150"
            ><template #default="scope">{{
              scope.row.skuName || '未明确'
            }}</template></el-table-column
          >
          <el-table-column label="分类" prop="categoryName" min-width="120" />
          <el-table-column label="价格" width="110"
            ><template #default="scope">{{
              scope.row.price == null ? '-' : `¥${Number(scope.row.price).toFixed(2)}`
            }}</template></el-table-column
          >
        </el-table>
        <el-descriptions title="提交与分配" :column="2" border class="mt-20px">
          <el-descriptions-item label="提交人">{{
            userText(detail.sourceUserId, detail.sourceUserName)
          }}</el-descriptions-item>
          <el-descriptions-item label="负责人">{{
            userText(detail.ownerUserId, detail.ownerUserName)
          }}</el-descriptions-item>
          <el-descriptions-item label="派单方式">{{
            dispatchModeLabel(detail.dispatchMode)
          }}</el-descriptions-item>
          <el-descriptions-item label="待接单人">{{
            userText(detail.pendingAssigneeUserId, detail.pendingAssigneeUserName)
          }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{
            formatZsjosTimestamp(detail.submittedAt)
          }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{
            formatZsjosTimestamp(detail.updateTime)
          }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.invalidReason" label="无效原因" :span="2">{{
            snapshotLabel(detail.invalidReasonLabelSnapshot, detail.invalidReason)
          }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.invalidDescription" label="判定备注" :span="2">{{
            detail.invalidDescription
          }}</el-descriptions-item>
        </el-descriptions>
        <div class="detail-heading">附件</div>
        <el-empty v-if="!detail.attachments?.length" description="暂无附件" :image-size="72" />
        <div v-else class="attachment-grid">
          <div v-for="(file, index) in detail.attachments" :key="file.id" class="attachment-item">
            <el-image
              :src="file.fileUrl"
              :alt="file.originalName"
              :preview-src-list="detail.attachments.map((item) => item.fileUrl)"
              :initial-index="index"
              fit="cover"
              class="attachment-image"
              preview-teleported
            />
            <span>{{ file.originalName }}</span>
          </div>
        </div>
        <div class="detail-heading">跟进时间线</div>
        <el-alert
          v-if="followUpError"
          type="error"
          :title="followUpError"
          show-icon
          :closable="false"
        />
        <div v-loading="followUpLoading">
          <el-empty
            v-if="!followUpLoading && !followUps.length"
            description="暂无跟进记录"
            :image-size="72"
          />
          <el-timeline v-else>
            <el-timeline-item
              v-for="record in followUps"
              :key="record.id"
              :timestamp="formatZsjosTimestamp(record.occurredAt)"
              placement="top"
            >
              <div class="follow-up-heading"
                ><strong>{{ record.operatorName || `用户 #${record.operatorUserId}` }}</strong
                ><el-tag v-if="record.firstInAssignment" type="success" size="small"
                  >本轮首次跟进</el-tag
                ></div
              >
              <div class="follow-up-tags"
                ><el-tag size="small">{{ snapshotLabel(record.methodLabel, record.method) }}</el-tag
                ><el-tag type="primary" size="small">{{
                  snapshotLabel(record.resultLabel, record.result)
                }}</el-tag></div
              >
              <div v-if="record.categoryBefore !== record.categoryAfter"
                >分类：{{ snapshotLabel(record.categoryBeforeLabel, record.categoryBefore) }} →
                {{ snapshotLabel(record.categoryAfterLabel, record.categoryAfter) }}</div
              >
              <p v-if="record.remark">{{ record.remark }}</p>
              <div v-if="record.nextFollowUpAt" class="follow-up-next"
                >下次跟进：{{ formatZsjosTimestamp(record.nextFollowUpAt) }}</div
              >
              <div v-if="record.images?.length" class="follow-up-images"
                ><el-image
                  v-for="(image, imageIndex) in record.images"
                  :key="image.infraFileId"
                  :src="image.url"
                  :preview-src-list="record.images.map((item) => item.url || '')"
                  :initial-index="imageIndex"
                  fit="cover"
                  preview-teleported
              /></div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </template>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import type { FormInstance } from 'element-plus'
import * as LeadApi from '@/api/zsjos/leadManagement'
import * as LeadFollowUpApi from '@/api/zsjos/leadFollowUp'
import { DICT_TYPE, getStrDictOptions } from '@/utils/dict'
import { formatZsjosTimestamp } from '@/utils/zsjosTime'
import type { AdvancedFilterGroup } from '@/api/zsjos/advancedFilter'
import ZsjosAdvancedFilter from '../components/ZsjosAdvancedFilter.vue'

defineOptions({ name: 'ZsjosLeadManagement' })

const zsjosLeadSourceChannel = computed(() =>
  getStrDictOptions(DICT_TYPE.ZSJOS_LEAD_SOURCE_CHANNEL)
)
const zsjosLeadCategory = computed(() => getStrDictOptions(DICT_TYPE.ZSJOS_LEAD_CATEGORY))
const queryFormRef = ref<FormInstance>()
const loading = ref(false)
const error = ref('')
const list = ref<LeadApi.LeadManagementVO[]>([])
const total = ref(0)
const users = ref<LeadApi.VisibleUserVO[]>([])
const queryParams = reactive<LeadApi.LeadManagementPageReqVO>({ pageNo: 1, pageSize: 10 })
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detail = ref<LeadApi.LeadManagementVO>()
const detailId = ref<number>()
const followUps = ref<LeadFollowUpApi.LeadFollowUpVO[]>([])
const followUpLoading = ref(false)
const followUpError = ref('')
const route = useRoute()

const getList = async () => {
  loading.value = true
  error.value = ''
  try {
    const data = await LeadApi.getLeadPage(queryParams)
    list.value = data.list || []
    total.value = data.total || 0
  } catch (e: any) {
    error.value = e?.msg || e?.message || '客资列表加载失败'
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}
const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}
const handleAdvancedSearch = (keyword: string) => { queryParams.keyword = keyword || undefined; handleQuery() }
const handleAdvancedFilter = (advancedFilter?: AdvancedFilterGroup) => { queryParams.advancedFilter = advancedFilter; handleQuery() }
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}
const openDetail = (row: LeadApi.LeadManagementVO) => {
  detailId.value = row.id
  detailVisible.value = true
  reloadDetail()
}
const reloadDetail = async () => {
  if (!detailId.value) return
  detailLoading.value = true
  followUpLoading.value = true
  detailError.value = ''
  detail.value = undefined
  followUps.value = []
  followUpError.value = ''
  try {
    const [leadResult, followUpResult] = await Promise.allSettled([
      LeadApi.getLead(detailId.value),
      LeadFollowUpApi.getLeadFollowUpPage(detailId.value)
    ])
    if (leadResult.status === 'rejected') throw leadResult.reason
    detail.value = leadResult.value
    if (followUpResult.status === 'fulfilled') followUps.value = followUpResult.value.list || []
    else
      followUpError.value =
        followUpResult.reason?.msg || followUpResult.reason?.message || '跟进记录加载失败'
  } catch (e: any) {
    detailError.value = e?.msg || e?.message || '客资详情加载失败'
  } finally {
    detailLoading.value = false
    followUpLoading.value = false
  }
}
const dictLabel = (options: Array<{ label: string; value: string | number }>, value?: string) =>
  !value ? '-' : options.find((item) => String(item.value) === String(value))?.label || '标签未配置'
const optionLabel = (options: Array<{ label: string; value: string }>, value?: string) =>
  !value ? '-' : options.find((item) => item.value === value)?.label || '未知状态'
const leadStatusLabel = (value?: string) => optionLabel(LeadApi.LEAD_STATUS_OPTIONS, value)
const assignmentStatusLabel = (value?: string) =>
  optionLabel(LeadApi.ASSIGNMENT_STATUS_OPTIONS, value)
const dispatchModeLabel = (value?: string) =>
  value ? LeadApi.DISPATCH_MODE_LABELS[value] || '未知派单方式' : '-'
const userText = (id?: number, name?: string) => name || (id ? `用户 #${id}` : '未分配')
const areaText = (row: LeadApi.LeadManagementVO) =>
  [row.provinceName, row.cityName].filter(Boolean).join(' / ') || '-'
const productText = (product?: LeadApi.LeadProductVO) =>
  product ? [product.spuName || '未明确课程', product.skuName].filter(Boolean).join(' / ') : '-'
const snapshotLabel = (label?: string, value?: string) =>
  !label || /^[a-z][a-z0-9_.-]*$/i.test(label) || label === value ? '标签未配置' : label

onMounted(async () => {
  const [userResult] = await Promise.allSettled([LeadApi.getVisibleUsers()])
  if (userResult.status === 'fulfilled') users.value = userResult.value
  await getList()
  const requestedLeadId = Number(route.query.leadId)
  if (Number.isFinite(requestedLeadId) && requestedLeadId > 0) {
    detailId.value = requestedLeadId
    detailVisible.value = true
    await reloadDetail()
  }
})
</script>

<style scoped>
.detail-heading {
  margin: 20px 0 10px;
  font-weight: 600;
}

.attachment-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 12px;
}

.attachment-item {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
  color: var(--el-text-color-regular);
}

.attachment-grid span {
  overflow: hidden;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-image {
  width: 100%;
  cursor: zoom-in;
  border-radius: 6px;
  aspect-ratio: 4 / 3;
}

.follow-up-heading,
.follow-up-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.follow-up-tags {
  margin: 8px 0;
}

.follow-up-next {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
}

.follow-up-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.follow-up-images .el-image {
  width: 72px;
  height: 72px;
  border-radius: 4px;
}

@media (width <= 768px) {
  :deep(.el-drawer) {
    width: 100% !important;
  }
}
</style>
