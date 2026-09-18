<template>
  <section v-loading="statusLoading">
    <h3>兼职账号</h3>
    <el-alert v-if="statusError" :title="statusError" type="error" :closable="false" show-icon>
      <template #default
        ><el-button link type="primary" @click="loadStatus">重试加载绑定状态</el-button></template
      >
    </el-alert>
    <el-descriptions v-else-if="status?.bound" :column="1" border>
      <el-descriptions-item label="兼职编号">{{ status.partnerNo }}</el-descriptions-item>
      <el-descriptions-item label="兼职姓名">{{ status.partnerName }}</el-descriptions-item>
      <el-descriptions-item label="绑定时间">{{
        status.startedAt ? formatDate(status.startedAt) : '-'
      }}</el-descriptions-item>
    </el-descriptions>
    <template v-else-if="status && !statusLoading">
      <el-text>未绑定兼职账号</el-text>
      <el-button class="ml-12px" type="primary" @click="open">绑定已有兼职账号</el-button>
    </template>
    <el-dialog
      v-model="visible"
      title="绑定已有兼职账号"
      width="min(720px, calc(100vw - 24px))"
      append-to-body
      :close-on-click-modal="false"
      :close-on-press-escape="!saving"
      :show-close="!saving"
      :before-close="close"
    >
      <div class="binding-body">
        <el-text
          >绑定学员：{{ studentName }}{{ studentMobile ? `（${studentMobile}）` : '' }}</el-text
        >
        <el-alert
          class="mt-12px mb-12px"
          type="info"
          :closable="false"
          show-icon
          title="请核对双方身份。绑定后账号主页汇总该兼职客资，原运营归属保持不变。"
        />
        <el-input
          v-model="keyword"
          aria-label="搜索兼职账号"
          placeholder="姓名、手机号或兼职编号"
          :disabled="saving"
          @keyup.enter="search"
        >
          <template #append
            ><el-button :disabled="saving" @click="search">搜索</el-button></template
          >
        </el-input>
        <el-alert
          v-if="listError"
          class="mt-12px"
          :title="listError"
          type="error"
          :closable="false"
          show-icon
        >
          <template #default
            ><el-button link @click="loadPartners">重试加载兼职</el-button></template
          >
        </el-alert>
        <el-table
          v-else
          v-loading="listLoading"
          :data="partners"
          row-key="id"
          class="mt-12px"
          empty-text="没有找到兼职账号，请调整搜索条件"
        >
          <el-table-column label="选择" width="70"
            ><template #default="{ row }">
              <el-radio
                :model-value="selected?.id"
                :value="row.id"
                :disabled="saving || listLoading"
                :aria-label="`选择${row.partnerNo}`"
                @change="selectPartner(row)"
                ><span></span
              ></el-radio> </template
          ></el-table-column>
          <el-table-column prop="partnerNo" label="兼职编号" min-width="150" />
          <el-table-column prop="name" label="姓名" min-width="100" />
          <el-table-column prop="mobile" label="手机号" min-width="130" />
        </el-table>
        <el-pagination
          v-if="!listError && total > 0"
          class="mt-12px"
          layout="prev, pager, next"
          :total="total"
          :page-size="10"
          :current-page="page"
          :disabled="saving"
          @current-change="changePage"
        />
        <p v-if="selected"
          >已选择：{{ selected.name }}（{{ selected.partnerNo }}，{{ selected.mobile }}）</p
        >
        <el-input
          v-model="reason"
          class="mt-12px"
          type="textarea"
          aria-label="绑定说明"
          placeholder="绑定说明（选填，最多500字）"
          :maxlength="500"
          :disabled="saving"
        />
        <el-alert
          v-if="saveError"
          class="mt-12px"
          :title="saveError"
          type="error"
          :closable="false"
          show-icon
        />
      </div>
      <template #footer>
        <el-button :disabled="saving" @click="visible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="saving"
          :disabled="!selected || listLoading || !!listError"
          @click="bind"
          >确认绑定</el-button
        >
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { formatDate } from '@/utils/formatTime'
import { getPartnerPage, type PartnerVO } from '@/api/zsjos/partner'
import {
  bindStudentPartner,
  getStudentPartnerBinding,
  type StudentPartnerBinding
} from '@/api/zsjos/partnerStudentLink'

const props = defineProps<{
  studentPersonId: number
  studentName?: string
  studentMobile?: string
}>()
const status = ref<StudentPartnerBinding>(),
  statusLoading = ref(false),
  statusError = ref('')
const visible = ref(false),
  saving = ref(false),
  saveError = ref('')
const partners = ref<PartnerVO[]>([]),
  selected = ref<PartnerVO>()
const keyword = ref(''),
  searchKeyword = ref(''),
  reason = ref(''),
  page = ref(1),
  total = ref(0)
const listLoading = ref(false),
  listError = ref('')
let statusRun = 0,
  listRun = 0,
  identity = 0
const errorText = (cause: unknown) => {
  if (typeof cause === 'string') return cause
  const error = cause as { msg?: string; message?: string }
  return error?.msg || error?.message || '操作失败，请重试'
}
const loadStatus = async () => {
  const run = ++statusRun
  statusLoading.value = true
  statusError.value = ''
  status.value = undefined
  try {
    const value = await getStudentPartnerBinding(props.studentPersonId)
    if (run === statusRun) status.value = value
  } catch (cause) {
    if (run === statusRun) statusError.value = errorText(cause)
  } finally {
    if (run === statusRun) statusLoading.value = false
  }
}
const loadPartners = async () => {
  const run = ++listRun
  listLoading.value = true
  listError.value = ''
  partners.value = []
  selected.value = undefined
  try {
    const value = await getPartnerPage(
      {
        pageNo: page.value,
        pageSize: 10,
        keyword: searchKeyword.value || undefined
      },
      true
    )
    if (run === listRun) {
      partners.value = value.list
      total.value = value.total
    }
  } catch (cause) {
    if (run === listRun) listError.value = errorText(cause)
  } finally {
    if (run === listRun) listLoading.value = false
  }
}
const open = () => {
  keyword.value = ''
  searchKeyword.value = ''
  reason.value = ''
  page.value = 1
  saveError.value = ''
  visible.value = true
  void loadPartners()
}
const close = (done: () => void) => {
  if (!saving.value) done()
}
const search = () => {
  if (!saving.value) {
    searchKeyword.value = keyword.value.trim()
    page.value = 1
    void loadPartners()
  }
}
const changePage = (value: number) => {
  page.value = value
  void loadPartners()
}
const selectPartner = (partner: PartnerVO) => {
  selected.value = partner
  saveError.value = ''
}
const bind = async () => {
  if (!selected.value || saving.value || listLoading.value || listError.value) return
  const current = identity
  saving.value = true
  saveError.value = ''
  try {
    await bindStudentPartner({
      partnerId: selected.value.id,
      studentPersonId: props.studentPersonId,
      reason: reason.value.trim() || undefined
    })
    if (current !== identity) return
    visible.value = false
    ElMessage.success('兼职账号已绑定')
    await loadStatus()
  } catch (cause) {
    if (current === identity) saveError.value = errorText(cause)
  } finally {
    if (current === identity) saving.value = false
  }
}
watch(
  () => props.studentPersonId,
  () => {
    identity++
    listRun++
    visible.value = false
    saving.value = false
    selected.value = undefined
    void loadStatus()
  },
  { immediate: true }
)
onBeforeUnmount(() => {
  identity++
  statusRun++
  listRun++
})
</script>

<style scoped>
.binding-body {
  max-height: calc(100dvh - 250px);
  overflow: auto;
}
</style>
