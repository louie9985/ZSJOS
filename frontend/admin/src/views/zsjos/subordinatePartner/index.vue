<template>
  <div class="subordinate-partner-page">
    <ContentWrap class="partner-pane">
      <el-input v-model="keyword" clearable placeholder="搜索兼职姓名或编号" @keyup.enter="searchPartners"><template #append><el-button @click="searchPartners"><Icon icon="ep:search" /></el-button></template></el-input>
      <el-alert v-if="partnerError" :title="partnerError" type="error" show-icon><template #default><el-button link @click="loadPartners">重试</el-button></template></el-alert>
      <el-table v-loading="partnerLoading" :data="partners" highlight-current-row @current-change="selectPartner">
        <el-table-column prop="partnerNo" label="兼职编号" min-width="120" />
        <el-table-column prop="name" label="姓名" min-width="100" />
        <el-table-column prop="status" label="状态" width="90" />
      </el-table>
      <Pagination :total="partnerTotal" v-model:page="partnerQuery.pageNo" v-model:limit="partnerQuery.pageSize" @pagination="loadPartners" />
    </ContentWrap>
    <ContentWrap class="lead-pane">
      <template v-if="selected">
        <div class="heading"><div><h3>{{ selected.name }}的客资</h3><span>提交时归属按客资快照展示</span></div><el-button @click="loadLeads"><Icon icon="ep:refresh" /></el-button></div>
        <el-alert v-if="leadError" :title="leadError" type="error" show-icon><template #default><el-button link @click="loadLeads">重试</el-button></template></el-alert>
        <el-table v-loading="leadLoading" :data="leads" empty-text="该兼职暂无客资">
          <el-table-column prop="leadNo" label="客资编号" min-width="150" />
          <el-table-column prop="submittedName" label="客户姓名" min-width="110" />
          <el-table-column prop="partnerOwnerNameSnapshot" label="提交时归属" min-width="120"><template #default="scope">{{ scope.row.partnerOwnerNameSnapshot || '未记录' }}</template></el-table-column>
          <el-table-column prop="ownerUserName" label="销售负责人" min-width="120"><template #default="scope">{{ scope.row.ownerUserName || '待分配' }}</template></el-table-column>
          <el-table-column label="操作" width="80"><template #default="scope"><el-button link type="primary" @click="openDetail(scope.row.id)">查看</el-button></template></el-table-column>
        </el-table>
        <Pagination :total="leadTotal" v-model:page="leadQuery.pageNo" v-model:limit="leadQuery.pageSize" @pagination="loadLeads" />
      </template>
      <el-empty v-else description="请选择兼职查看其全部客资" />
    </ContentWrap>
  </div>
  <el-drawer v-model="detailVisible" title="下属兼职客资详情" size="680px" destroy-on-close @closed="closeDetail">
    <div v-loading="detailLoading" class="detail-body">
      <el-alert v-if="detailError" :title="detailError" type="error" show-icon><template #default><el-button link @click="detailId && openDetail(detailId)">重试</el-button></template></el-alert>
      <el-descriptions v-else-if="detail" :column="2" border>
        <el-descriptions-item label="客资编号">{{ detail.leadNo }}</el-descriptions-item><el-descriptions-item label="客户姓名">{{ detail.submittedName }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detail.submittedMobile || '-' }}</el-descriptions-item><el-descriptions-item label="来源">{{ detail.sourceLabel || '兼职提交' }}</el-descriptions-item>
        <el-descriptions-item label="提交时归属">{{ detail.partnerOwnerNameSnapshot || '未记录' }}</el-descriptions-item><el-descriptions-item label="销售负责人">{{ detail.ownerUserName || '待分配' }}</el-descriptions-item>
        <el-descriptions-item label="客资分类">{{ detail.leadCategoryLabelSnapshot || '-' }}</el-descriptions-item><el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import * as Api from '@/api/zsjos/subordinatePartner'
defineOptions({ name: 'ZsjosSubordinatePartner' })
const keyword = ref(''); const appliedKeyword = ref(''); const partnerLoading = ref(false); const partnerError = ref(''); const partners = ref<Api.SubordinatePartnerVO[]>([]); const partnerTotal = ref(0); const partnerQuery = reactive({ pageNo: 1, pageSize: 20 })
const selected = ref<Api.SubordinatePartnerVO>(); const leadLoading = ref(false); const leadError = ref(''); const leads = ref<Api.SubordinateLeadVO[]>([]); const leadTotal = ref(0); const leadQuery = reactive({ pageNo: 1, pageSize: 20 })
const detailVisible = ref(false); const detailLoading = ref(false); const detailError = ref(''); const detail = ref<Api.SubordinateLeadVO>(); const detailId = ref<number>()
let partnerRequestVersion = 0; let leadRequestVersion = 0; let detailRequestVersion = 0
const errorMessage = (error: unknown, fallback: string) => {
  if (typeof error === 'object' && error !== null) {
    const value = error as { msg?: unknown; message?: unknown }
    if (typeof value.msg === 'string') return value.msg
    if (typeof value.message === 'string') return value.message
  }
  return fallback
}
const loadPartners = async () => {
  const requestVersion = ++partnerRequestVersion
  partnerLoading.value = true; partnerError.value = ''
  try {
    const page = await Api.getPage({ ...partnerQuery, keyword: appliedKeyword.value || undefined })
    if (requestVersion !== partnerRequestVersion) return
    partners.value = page.list; partnerTotal.value = page.total
    if (selected.value && !page.list.some((item) => item.id === selected.value?.id)) selectPartner()
  } catch (error: unknown) {
    if (requestVersion !== partnerRequestVersion) return
    partners.value = []; partnerTotal.value = 0; partnerError.value = errorMessage(error, '下属兼职加载失败')
  } finally {
    if (requestVersion === partnerRequestVersion) partnerLoading.value = false
  }
}
const searchPartners = () => { partnerQuery.pageNo = 1; appliedKeyword.value = keyword.value.trim(); loadPartners() }
const selectPartner = (row?: Api.SubordinatePartnerVO) => {
  ++leadRequestVersion; ++detailRequestVersion
  selected.value = row; leadQuery.pageNo = 1; leads.value = []; leadTotal.value = 0; leadError.value = ''; leadLoading.value = false
  detailVisible.value = false; detail.value = undefined; detailId.value = undefined; detailError.value = ''; detailLoading.value = false
  if (row) loadLeads()
}
const loadLeads = async () => {
  const partner = selected.value
  if (!partner) return
  const requestVersion = ++leadRequestVersion
  leadLoading.value = true; leadError.value = ''
  try {
    const page = await Api.getLeadPage(partner.id, leadQuery)
    if (requestVersion !== leadRequestVersion) return
    leads.value = page.list; leadTotal.value = page.total
  } catch (error: unknown) {
    if (requestVersion !== leadRequestVersion) return
    leads.value = []; leadTotal.value = 0; leadError.value = errorMessage(error, '兼职客资加载失败')
  } finally {
    if (requestVersion === leadRequestVersion) leadLoading.value = false
  }
}
const openDetail = async (id: number) => {
  const requestVersion = ++detailRequestVersion
  detailId.value = id; detailVisible.value = true; detailLoading.value = true; detailError.value = ''; detail.value = undefined
  try {
    const result = await Api.getLead(id)
    if (requestVersion === detailRequestVersion) detail.value = result
  } catch (error: unknown) {
    if (requestVersion === detailRequestVersion) detailError.value = errorMessage(error, '客资详情加载失败')
  } finally {
    if (requestVersion === detailRequestVersion) detailLoading.value = false
  }
}
const closeDetail = () => { ++detailRequestVersion; detail.value = undefined; detailId.value = undefined; detailError.value = ''; detailLoading.value = false }
onMounted(loadPartners)
onBeforeUnmount(() => { ++partnerRequestVersion; ++leadRequestVersion; ++detailRequestVersion })
</script>

<style scoped>
.subordinate-partner-page { display: grid; grid-template-columns: minmax(300px, 36%) minmax(0, 1fr); gap: 16px; }
.heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.heading h3 { margin: 0; font-size: 16px; }.heading span { color: var(--el-text-color-secondary); font-size: 13px; }
.detail-body { min-height: 120px; }
@media (max-width: 760px) { .subordinate-partner-page { grid-template-columns: 1fr; } }
</style>
