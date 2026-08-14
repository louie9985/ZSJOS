<template>
  <ContentWrap>
    <div class="pool-toolbar">
      <div>
        <div class="pool-title">客资抢单池</div>
        <div class="pool-subtitle">按进入抢单池时间由早到晚展示，联系方式均已脱敏</div>
      </div>
      <el-button :loading="loading" @click="getList"> <Icon icon="ep:refresh" />刷新 </el-button>
    </div>
    <ZsjosAdvancedFilter scene="lead" placeholder="姓名 / 手机号 / 微信号" :keyword="queryParams.keyword" @search="handleSearch" @change="handleFilter" />
  </ContentWrap>

  <ContentWrap>
    <el-alert
      v-if="error"
      type="error"
      :title="unauthorized ? '无权查看抢单池' : '抢单池加载失败'"
      :description="error"
      show-icon
      :closable="false"
      class="mb-16px"
    >
      <template v-if="!unauthorized" #default>
        <el-button link type="primary" @click="getList">重新加载</el-button>
      </template>
    </el-alert>

    <el-table
      v-loading="loading"
      :data="list"
      stripe
      empty-text="当前没有抢单池客资"
      table-layout="fixed"
    >
      <el-table-column label="客资编号" prop="leadNo" width="220" fixed="left" />
      <el-table-column label="客户" min-width="120" fixed="left">
        <template #default="scope">
          <span class="customer-name">{{ scope.row.maskedName || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="脱敏手机号" prop="maskedMobile" min-width="140">
        <template #default="scope">{{ scope.row.maskedMobile || '-' }}</template>
      </el-table-column>
      <el-table-column label="脱敏微信号" prop="maskedWechatId" min-width="150">
        <template #default="scope">{{ scope.row.maskedWechatId || '-' }}</template>
      </el-table-column>
      <el-table-column label="完整地区" min-width="160">
        <template #default="scope">{{ areaText(scope.row) }}</template>
      </el-table-column>
      <el-table-column label="意向课程" min-width="280">
        <template #default="scope">
          <div v-if="scope.row.intendedProducts?.length" class="course-tags">
            <el-tag
              v-for="(course, index) in scope.row.intendedProducts"
              :key="`${course}-${index}`"
              :type="course === scope.row.primaryIntendedProduct ? 'primary' : 'info'"
              effect="light"
            >
              {{ course
              }}<template v-if="course === scope.row.primaryIntendedProduct"> · 主意向</template>
            </el-tag>
          </div>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="来源渠道" prop="sourceChannel" min-width="130">
        <template #default="scope">{{ scope.row.sourceChannel || '-' }}</template>
      </el-table-column>
      <el-table-column label="客资分类" prop="leadCategory" min-width="130">
        <template #default="scope">{{ scope.row.leadCategory || '-' }}</template>
      </el-table-column>
      <el-table-column label="完整备注" min-width="300">
        <template #default="scope">
          <div class="remark-cell">{{ scope.row.remark || '-' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="附件图片" min-width="260">
        <template #default="scope">
          <div v-if="scope.row.attachmentUrls?.length" class="attachment-list">
            <el-image
              v-for="(url, index) in scope.row.attachmentUrls"
              :key="`${url}-${index}`"
              :src="url"
              :preview-src-list="scope.row.attachmentUrls"
              :initial-index="Number(index)"
              fit="cover"
              lazy
              preview-teleported
            />
          </div>
          <span v-else>-</span>
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
</template>

<script setup lang="ts">
import * as ClaimPoolApi from '@/api/zsjos/leadClaimPool'
import type { AdvancedFilterGroup } from '@/api/zsjos/advancedFilter'
import ZsjosAdvancedFilter from '../components/ZsjosAdvancedFilter.vue'

defineOptions({ name: 'ZsjosLeadClaimPool' })

const loading = ref(false)
const error = ref('')
const list = ref<ClaimPoolApi.LeadClaimPoolVO[]>([])
const total = ref(0)
const queryParams = reactive<ClaimPoolApi.LeadClaimPoolPageReqVO>({ pageNo: 1, pageSize: 20 })
const unauthorized = computed(
  () => error.value.includes('403') || error.value.includes('无权') || error.value.includes('权限')
)
let requestVersion = 0

const getList = async () => {
  const version = ++requestVersion
  loading.value = true
  error.value = ''
  try {
    const data = await ClaimPoolApi.getClaimPoolPage(queryParams)
    if (version !== requestVersion) return
    list.value = data.list || []
    total.value = data.total || 0
  } catch (e: any) {
    if (version === requestVersion) error.value = e?.msg || e?.message || '抢单池加载失败'
  } finally {
    if (version === requestVersion) loading.value = false
  }
}
const handleSearch = (keyword: string) => { queryParams.keyword = keyword || undefined; queryParams.pageNo = 1; void getList() }
const handleFilter = (advancedFilter?: AdvancedFilterGroup) => { queryParams.advancedFilter = advancedFilter; queryParams.pageNo = 1; void getList() }

const areaText = (row: ClaimPoolApi.LeadClaimPoolVO) =>
  [row.provinceName, row.cityName].filter(Boolean).join(' / ') || '-'

onMounted(getList)
</script>

<style scoped>
.pool-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.pool-title {
  font-size: 18px;
  font-weight: 600;
}

.pool-subtitle {
  margin-top: 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.customer-name {
  font-weight: 600;
}

.course-tags,
.attachment-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 6px 0;
}

.course-tags :deep(.el-tag) {
  height: auto;
  min-height: 24px;
  white-space: normal;
}

.remark-cell {
  padding: 8px 0;
  line-height: 1.6;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.attachment-list :deep(.el-image) {
  width: 64px;
  height: 64px;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

@media (width <= 768px) {
  .pool-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
