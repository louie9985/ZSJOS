<template>
  <ContentWrap>
    <el-form :model="query" inline @submit.prevent>
      <el-form-item label="学员搜索">
        <el-input
          v-model="query.keyword"
          clearable
          class="!w-280px"
          placeholder="姓名、手机号或客资编号"
          @keyup.enter="handleQuery"
        />
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
    scene="student"
    placeholder="姓名 / 手机号 / 客资编号"
    :keyword="query.keyword"
    @search="(value) => { query.keyword = value; handleQuery() }"
    @change="(value) => { query.advancedFilter = value; handleQuery() }"
  />

  <ContentWrap>
    <el-table v-loading="loading" :data="list" row-key="personId" stripe>
      <el-table-column label="学员姓名" prop="name" min-width="130" fixed="left" />
      <el-table-column label="手机号" min-width="130">
        <template #default="{ row }">{{ row.mobile || '-' }}</template>
      </el-table-column>
      <el-table-column label="微信号" min-width="140">
        <template #default="{ row }">{{ row.wechatId || '-' }}</template>
      </el-table-column>
      <el-table-column label="客资编号" min-width="210">
        <template #default="{ row }">{{ row.leadNo || '客资编号暂未生成' }}</template>
      </el-table-column>
      <el-table-column label="在服务课程" min-width="220">
        <template #default="{ row }">{{ serviceSummary(row.services) }}</template>
      </el-table-column>
      <el-table-column label="转为学员时间" prop="activatedAt" min-width="170" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row.personId)">详情</el-button>
        </template>
      </el-table-column>
      <template #empty><el-empty description="暂无学员" /></template>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load"
    />
  </ContentWrap>

  <el-drawer v-model="detailOpen" title="学员详情" size="720px" destroy-on-close>
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
          <el-descriptions-item label="学员姓名">{{ detail.name }}</el-descriptions-item>
          <el-descriptions-item label="客资编号">{{
            detail.leadNo || '客资编号暂未生成'
          }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ detail.mobile || '-' }}</el-descriptions-item>
          <el-descriptions-item label="微信号">{{ detail.wechatId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="转为学员时间" :span="2">{{
            detail.activatedAt || '-'
          }}</el-descriptions-item>
        </el-descriptions>

        <div class="section-heading">课程服务</div>
        <el-table :data="detail.services" row-key="serviceRelationId" border>
          <el-table-column label="订单编号" prop="orderNo" min-width="190" />
          <el-table-column label="课程权益" min-width="320">
            <template #default="{ row }">
              <div class="course-rights-cell">
                <strong>{{ serviceCourseName(row) }}</strong>
                <span v-if="row.skuName && row.skuName !== row.courseName">{{ row.skuName }}</span>
                <span>{{ serviceCategoryPath(row) }}</span>
                <div v-if="row.attributeValues?.length" class="course-rights-tags">
                  <el-tag v-for="value in row.attributeValues" :key="value" size="small">{{
                    value
                  }}</el-tag>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="服务状态" width="110">
            <template #default="{ row }"
              ><el-tag :type="row.status === 'active' ? 'success' : 'info'">{{
                serviceStatusLabel(row.status)
              }}</el-tag></template
            >
          </el-table-column>
          <el-table-column label="生效时间" prop="activatedAt" min-width="170" />
          <template #empty><el-empty description="暂无课程服务" /></template>
        </el-table>
      </template>
    </div>
  </el-drawer>
</template>

<script lang="ts" setup>
import * as RegistrationApi from '@/api/zsjos/registration'
import ZsjosAdvancedFilter from './components/ZsjosAdvancedFilter.vue'

defineOptions({ name: 'ZsjosMyStudents' })

const loading = ref(false)
const error = ref('')
const list = ref<RegistrationApi.MyStudent[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 10, keyword: '', advancedFilter: undefined as any })
const detailOpen = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detail = ref<RegistrationApi.MyStudent>()
const detailPersonId = ref<number>()

const serviceSummary = (services: RegistrationApi.StudentService[]) => {
  if (!services?.length) return '暂无课程服务'
  const names = services.map(serviceCourseName).filter(Boolean)
  return names.length > 2
    ? `${names.slice(0, 2).join('、')} 等 ${names.length} 项`
    : names.join('、')
}
const serviceCourseName = (service: RegistrationApi.StudentService) =>
  service.courseName || service.skuName || '课程服务'
const serviceCategoryPath = (service: RegistrationApi.StudentService) =>
  service.categoryPath?.length ? service.categoryPath.join(' / ') : '课程分类暂未记录'
const serviceStatusLabel = (status: string) =>
  ({ active: '服务中', completed: '已完成', cancelled: '已取消' })[status] || '未知状态'
const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const data = await RegistrationApi.getMyStudentPage({
      ...query,
      keyword: query.keyword.trim() || undefined
    })
    list.value = data.list
    total.value = data.total
  } catch (cause: any) {
    list.value = []
    total.value = 0
    error.value = cause?.msg || cause?.message || '学员列表加载失败'
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
  query.keyword = ''
  query.advancedFilter = undefined
  void load()
}
const openDetail = async (personId: number) => {
  detailPersonId.value = personId
  detailOpen.value = true
  detailLoading.value = true
  detailError.value = ''
  try {
    detail.value = await RegistrationApi.getMyStudent(personId)
  } catch (cause: any) {
    detail.value = undefined
    detailError.value = cause?.msg || cause?.message || '学员详情加载失败'
  } finally {
    detailLoading.value = false
  }
}
const reloadDetail = () => detailPersonId.value && openDetail(detailPersonId.value)

onMounted(load)
</script>

<style scoped>
.section-heading {
  margin: 24px 0 12px;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.course-rights-cell {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 4px;
}

.course-rights-cell span {
  color: var(--el-text-color-secondary);
  overflow-wrap: anywhere;
}

.course-rights-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
</style>
