<template>
  <ContentWrap>
    <el-form :model="queryParams" inline class="mb-16px" @submit.prevent="handleQuery">
      <el-form-item label="关键词">
        <el-input
          v-model="queryParams.keyword"
          clearable
          placeholder="姓名、编号或手机号"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" clearable class="!w-160px" placeholder="全部状态">
          <el-option label="启用" value="enabled" />
          <el-option label="停用" value="disabled" />
          <el-option label="已转员工" value="converted" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleQuery"><Icon icon="ep:search" />查询</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" />重置</el-button>
        <el-button
          v-hasPermi="['zsjos:partner:manage']"
          type="primary"
          @click="createVisible = true"
        >
          <Icon icon="ep:plus" />新增兼职
        </el-button>
        <el-button
          v-hasPermi="['zsjos:partner-invitation:create']"
          type="success"
          @click="openInvitationCreate"
        >
          <Icon icon="ep:key" />生成邀请码
        </el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="partners" class="table">
      <el-table-column prop="partnerNo" label="兼职编号" />
      <el-table-column prop="name" label="姓名" />
      <el-table-column prop="mobile" label="手机号" />
      <el-table-column label="当前归属" min-width="150"
        ><template #default="scope"
          ><span>{{ scope.row.assignedEmployeeName || '未分配' }}</span
          ><el-tag
            v-if="scope.row.assignedEmployeeName && scope.row.assignmentEffective === false"
            type="warning"
            class="ml-8px"
            >授权失效</el-tag
          ></template
        ></el-table-column
      >
      <el-table-column label="状态"
        ><template #default="scope">{{ statusLabels[scope.row.status] }}</template></el-table-column
      >
      <el-table-column v-hasPermi="['zsjos:partner:manage']" label="操作" width="420">
        <template #default="scope">
          <el-button link type="primary" @click="openAssignment(scope.row)">归属</el-button>
          <el-button link @click="openAssignmentLogs(scope.row)">历史</el-button>
          <el-button
            v-if="scope.row.status === 'enabled'"
            link
            type="warning"
            @click="changeState(scope.row, false)"
            >停用</el-button
          >
          <el-button
            v-if="scope.row.status === 'disabled'"
            link
            type="success"
            @click="changeState(scope.row, true)"
            >启用</el-button
          >
          <el-button
            v-if="scope.row.status === 'enabled'"
            link
            type="primary"
            @click="openConvert(scope.row)"
            >转为员工</el-button
          >
          <el-button v-if="scope.row.status !== 'converted'" link @click="changeMobile(scope.row)"
            >改手机号</el-button
          >
          <el-button v-if="scope.row.status !== 'converted'" link @click="resetPassword(scope.row)"
            >重置密码</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <el-alert v-if="loadError" :title="loadError" type="error" show-icon>
      <template #default><el-button link @click="load">重试</el-button></template>
    </el-alert>
    <el-empty v-else-if="!loading && !partners.length" description="暂无兼职主体" />
    <Pagination
      v-if="total > 0"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      :total="total"
      @pagination="load"
    />
  </ContentWrap>
  <ContentWrap v-hasPermi="['zsjos:partner-invitation:query']" class="mt-16px">
    <template #header>
      <span>兼职邀请码</span>
    </template>
    <el-form :model="invitationQuery" inline class="mb-16px" @submit.prevent="handleInvitationQuery">
      <el-form-item label="关键词">
        <el-input
          v-model="invitationQuery.keyword"
          clearable
          placeholder="姓名、手机号或邀请码"
          @keyup.enter="handleInvitationQuery"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="invitationQuery.status" clearable class="!w-160px" placeholder="全部状态">
          <el-option label="待激活" value="active" />
          <el-option label="已使用" value="used" />
          <el-option label="已失效" value="voided" />
          <el-option label="已过期" value="expired" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleInvitationQuery">
          <Icon icon="ep:search" />查询
        </el-button>
        <el-button @click="resetInvitationQuery">
          <Icon icon="ep:refresh" />重置
        </el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="invitationLoading" :data="invitations">
      <el-table-column label="邀请码" width="160">
        <template #default="scope">
          <span>{{ scope.row.inviteCode }}</span>
          <el-tooltip content="复制邀请码" placement="top">
            <el-button
              link
              type="primary"
              class="ml-4px"
              aria-label="复制邀请码"
              @click="copyInvitationCode(scope.row.inviteCode)"
            >
              <Icon icon="ep:copy-document" />
            </el-button>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="姓名" />
      <el-table-column prop="mobile" label="手机号" />
      <el-table-column prop="assignedOperatorName" label="归属运营" />
      <el-table-column label="状态" width="100">
        <template #default="scope">{{ invitationStatusLabels[scope.row.status] }}</template>
      </el-table-column>
      <el-table-column prop="expiresAt" label="过期时间" min-width="170" />
      <el-table-column prop="createdByName" label="创建人" />
      <el-table-column label="操作" width="100">
        <template #default="scope">
          <el-button
            v-if="scope.row.status === 'active'"
            v-hasPermi="['zsjos:partner-invitation:void']"
            link
            type="warning"
            @click="handleVoidInvitation(scope.row)"
            >作废</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <el-alert v-if="invitationLoadError" :title="invitationLoadError" type="error" show-icon>
      <template #default><el-button link @click="loadInvitations">重试</el-button></template>
    </el-alert>
    <el-empty v-else-if="!invitationLoading && !invitations.length" description="暂无邀请码" />
    <Pagination
      v-if="invitationTotal > 0"
      v-model:page="invitationQuery.pageNo"
      v-model:limit="invitationQuery.pageSize"
      :total="invitationTotal"
      @pagination="loadInvitations"
    />
  </ContentWrap>
  <Dialog v-model="createVisible" title="新增兼职账号">
    <el-form :model="createForm" label-width="100px">
      <el-form-item label="兼职编号"><el-input v-model="createForm.partnerNo" /></el-form-item>
      <el-form-item label="姓名"><el-input v-model="createForm.name" /></el-form-item>
      <el-form-item label="手机号"
        ><el-input v-model="createForm.mobile" maxlength="11"
      /></el-form-item>
      <el-form-item label="初始密码" required
        ><el-input v-model="createForm.password" type="password" show-password maxlength="20"
      /></el-form-item>
      <el-form-item label="渠道编号"
        ><el-input v-model="createForm.channelId" maxlength="64"
      /></el-form-item>
    </el-form>
    <template #footer
      ><el-button @click="createVisible = false">取消</el-button
      ><el-button type="primary" @click="submitCreate">确认</el-button></template
    >
  </Dialog>
  <Dialog v-model="invitationCreateVisible" title="生成兼职邀请码" width="520px">
    <el-form :model="invitationForm" label-width="100px">
      <el-form-item label="姓名" required>
        <el-input v-model="invitationForm.name" maxlength="100" />
      </el-form-item>
      <el-form-item label="手机号" required>
        <el-input v-model="invitationForm.mobile" maxlength="11" />
      </el-form-item>
      <el-form-item label="归属运营" required>
        <el-select
          v-model="invitationForm.assignedOperatorUserId"
          filterable
          remote
          reserve-keyword
          :remote-method="searchInvitationOperators"
          :loading="operatorLoading"
          placeholder="选择新媒体运营"
        >
          <el-option
            v-for="user in invitationOperators"
            :key="user.id"
            :label="user.nickname"
            :value="user.id"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="invitationCreateVisible = false">取消</el-button>
      <el-button type="primary" :loading="invitationSubmitting" @click="submitInvitation">
        生成
      </el-button>
    </template>
  </Dialog>
  <Dialog v-model="convertVisible" title="兼职转为新媒体员工">
    <el-form :model="convertForm" label-width="120px">
      <el-form-item label="员工类型" required
        ><el-radio-group v-model="convertForm.targetType"
          ><el-radio-button value="new_media_employee">新媒体员工</el-radio-button
          ><el-radio-button value="new_media_manager">新媒体主管</el-radio-button></el-radio-group
        ></el-form-item
      >
      <el-form-item label="员工用户名" required
        ><el-input v-model="convertForm.username" maxlength="32"
      /></el-form-item>
      <el-form-item label="员工初始密码" required
        ><el-input v-model="convertForm.password" type="password" show-password maxlength="20"
      /></el-form-item>
      <el-form-item label="归属部门" required
        ><el-tree-select
          v-model="convertForm.deptId"
          :data="deptList"
          :props="defaultProps"
          node-key="id"
          check-strictly
      /></el-form-item>
      <el-form-item label="迁移历史快照"
        ><el-switch v-model="convertForm.migrateHistoricalOrganization"
      /></el-form-item>
      <el-form-item label="转换原因" required
        ><el-input v-model="convertForm.reason" type="textarea" maxlength="500"
      /></el-form-item>
    </el-form>
    <template #footer
      ><el-button @click="convertVisible = false">取消</el-button
      ><el-button type="primary" @click="submitConvert">确认转换</el-button></template
    >
  </Dialog>
  <Dialog v-model="assignmentVisible" title="设置兼职归属">
    <el-form label-width="100px">
      <el-form-item label="归属员工"
        ><el-select
          v-model="assignmentUserId"
          clearable
          filterable
          :loading="assignmentLoading"
          placeholder="留空表示解除归属"
          ><el-option
            v-for="user in assignmentCandidates"
            :key="user.id"
            :label="user.nickname"
            :value="user.id" /></el-select
      ></el-form-item>
      <el-form-item label="调整原因" required
        ><el-input
          v-model="assignmentReason"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
      /></el-form-item>
    </el-form>
    <template #footer
      ><el-button @click="assignmentVisible = false">取消</el-button
      ><el-button
        type="primary"
        :loading="assignmentLoading"
        :disabled="!assignmentReason.trim()"
        @click="submitAssignment"
        >确认</el-button
      ></template
    >
  </Dialog>
  <Dialog v-model="assignmentLogVisible" title="兼职归属历史" width="760px">
    <el-table v-loading="assignmentLogLoading" :data="assignmentLogs" empty-text="暂无归属变更"
      ><el-table-column label="变更前"
        ><template #default="scope">{{
          scope.row.previousEmployeeName || '未分配'
        }}</template></el-table-column
      ><el-table-column label="变更后"
        ><template #default="scope">{{
          scope.row.employeeName || '未分配'
        }}</template></el-table-column
      ><el-table-column
        prop="reason"
        label="原因"
        min-width="180"
        show-overflow-tooltip /><el-table-column
        prop="operatorName"
        label="操作人" /><el-table-column prop="occurredAt" label="时间" min-width="170"
    /></el-table>
  </Dialog>
</template>
<script lang="ts" setup>
import * as PartnerApi from '@/api/zsjos/partner'
import * as DeptApi from '@/api/system/dept'
import { defaultProps, handleTree } from '@/utils/tree'
import { useClipboard } from '@vueuse/core'

defineOptions({ name: 'ZsjosPartner' })
const message = useMessage()
const loading = ref(false)
const partners = ref<PartnerApi.PartnerVO[]>([])
const total = ref(0)
const loadError = ref('')
const queryParams = reactive({ pageNo: 1, pageSize: 20, keyword: '', status: '' })
const statusLabels = { enabled: '启用', disabled: '停用', converted: '已转员工' }
const invitationStatusLabels = {
  active: '待激活',
  used: '已使用',
  voided: '已失效',
  expired: '已过期'
}
const createVisible = ref(false)
const invitationCreateVisible = ref(false)
const invitationSubmitting = ref(false)
const invitationLoading = ref(false)
const invitationLoadError = ref('')
const invitationTotal = ref(0)
const invitations = ref<PartnerApi.PartnerInvitationVO[]>([])
const invitationQuery = reactive({ pageNo: 1, pageSize: 10, keyword: '', status: '' })
const invitationForm = reactive<PartnerApi.PartnerInvitationCreateVO>({
  name: '',
  mobile: '',
  assignedOperatorUserId: undefined
})
const operatorLoading = ref(false)
const invitationOperators = ref<PartnerApi.AssignmentCandidateVO[]>([])
const convertVisible = ref(false)
const assignmentVisible = ref(false)
const assignmentLoading = ref(false)
const assignmentPartner = ref<PartnerApi.PartnerVO>()
const assignmentUserId = ref<number>()
const assignmentReason = ref('')
const assignmentCandidates = ref<PartnerApi.AssignmentCandidateVO[]>([])
const assignmentLogVisible = ref(false)
const assignmentLogLoading = ref(false)
const assignmentLogs = ref<PartnerApi.AssignmentLogVO[]>([])
const selectedId = ref<number>()
const deptList = ref<Tree[]>([])
const createForm = reactive<PartnerApi.PartnerCreateVO>({
  partnerNo: '',
  name: '',
  mobile: '',
  password: '',
  channelId: ''
})
const convertForm = reactive({
  targetType: 'new_media_employee',
  username: '',
  password: '',
  deptId: undefined as number | undefined,
  migrateHistoricalOrganization: false,
  reason: ''
})
const load = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const page = await PartnerApi.getPartnerPage({
      ...queryParams,
      keyword: queryParams.keyword.trim() || undefined,
      status: queryParams.status || undefined
    })
    partners.value = page.list
    total.value = page.total
  } catch (cause: any) {
    partners.value = []
    total.value = 0
    loadError.value = cause?.code === 403 ? '无权查看兼职信息' : '兼职列表加载失败'
  } finally {
    loading.value = false
  }
}
const loadInvitations = async () => {
  invitationLoading.value = true
  invitationLoadError.value = ''
  try {
    const page = await PartnerApi.getInvitationPage({
      ...invitationQuery,
      keyword: invitationQuery.keyword.trim() || undefined,
      status: invitationQuery.status || undefined
    })
    invitations.value = page.list
    invitationTotal.value = page.total
  } catch (cause: any) {
    invitations.value = []
    invitationTotal.value = 0
    invitationLoadError.value = cause?.code === 403 ? '无权查看兼职邀请码' : '邀请码列表加载失败'
  } finally {
    invitationLoading.value = false
  }
}
const submitCreate = async () => {
  if (
    !/^1\d{10}$/.test(createForm.mobile) ||
    !/^(?=.*[A-Za-z])(?=.*\d).{8,20}$/.test(createForm.password)
  )
    return message.warning('请检查手机号及密码规则')
  await PartnerApi.createPartner(createForm)
  createVisible.value = false
  message.success('兼职账号已创建')
  await load()
}
const openInvitationCreate = async () => {
  invitationForm.name = ''
  invitationForm.mobile = ''
  invitationForm.assignedOperatorUserId = undefined
  invitationCreateVisible.value = true
  await searchInvitationOperators('')
}
const searchInvitationOperators = async (keyword: string) => {
  operatorLoading.value = true
  try {
    invitationOperators.value = (await PartnerApi.getInvitationOperatorCandidates({ keyword })).list
  } catch {
    invitationOperators.value = []
    message.error('归属运营加载失败')
  } finally {
    operatorLoading.value = false
  }
}
const submitInvitation = async () => {
  if (
    !invitationForm.name.trim() ||
    !/^1\d{10}$/.test(invitationForm.mobile) ||
    !invitationForm.assignedOperatorUserId
  )
    return message.warning('请完整填写姓名、手机号和归属运营')
  invitationSubmitting.value = true
  try {
    const result = await PartnerApi.createInvitation({
      name: invitationForm.name.trim(),
      mobile: invitationForm.mobile.trim(),
      assignedOperatorUserId: invitationForm.assignedOperatorUserId
    })
    invitationCreateVisible.value = false
    message.success(`邀请码已生成：${result.inviteCode}`)
    await Promise.all([loadInvitations(), load()])
  } finally {
    invitationSubmitting.value = false
  }
}
const handleInvitationQuery = () => {
  invitationQuery.pageNo = 1
  loadInvitations()
}
const copyInvitationCode = async (inviteCode: string) => {
  const { copy, copied, isSupported } = useClipboard({ legacy: true, source: inviteCode })
  if (!isSupported.value) {
    message.error('复制邀请码失败')
    return
  }
  try {
    await copy()
    if (copied.value) message.success('邀请码已复制')
    else message.error('复制邀请码失败')
  } catch {
    message.error('复制邀请码失败')
  }
}
const resetInvitationQuery = () => {
  invitationQuery.pageNo = 1
  invitationQuery.keyword = ''
  invitationQuery.status = ''
  loadInvitations()
}
const handleVoidInvitation = async (row: PartnerApi.PartnerInvitationVO) => {
  await message.confirm(`确认作废邀请码 ${row.inviteCode}？`)
  await PartnerApi.voidInvitation(row.id)
  message.success('邀请码已作废')
  await loadInvitations()
}
const changeState = async (row: PartnerApi.PartnerVO, enable: boolean) => {
  const result = await message.prompt('请输入变更原因', enable ? '启用兼职' : '停用兼职')
  const reason = result.value.trim()
  if (!reason) return
  if (enable) await PartnerApi.enablePartner(row.id, reason)
  else await PartnerApi.disablePartner(row.id, reason)
  message.success('兼职状态已更新')
  await load()
}
const openConvert = async (row: PartnerApi.PartnerVO) => {
  selectedId.value = row.id
  deptList.value = handleTree(await DeptApi.getSimpleDeptList())
  convertVisible.value = true
}
const submitConvert = async () => {
  if (
    !selectedId.value ||
    !convertForm.deptId ||
    !convertForm.reason.trim() ||
    !/^[A-Za-z0-9_]{4,32}$/.test(convertForm.username) ||
    !/^(?=.*[A-Za-z])(?=.*\d).{8,20}$/.test(convertForm.password)
  )
    return message.warning('请完整填写转换信息')
  await PartnerApi.convertPartner(selectedId.value, { ...convertForm, deptId: convertForm.deptId })
  convertVisible.value = false
  message.success('兼职已转为员工')
  await load()
}
const handleQuery = () => {
  queryParams.pageNo = 1
  load()
}
const resetQuery = () => {
  queryParams.pageNo = 1
  queryParams.keyword = ''
  queryParams.status = ''
  load()
}
const openAssignment = async (row: PartnerApi.PartnerVO) => {
  assignmentPartner.value = row
  assignmentUserId.value = row.assignedEmployeeUserId
  assignmentReason.value = ''
  assignmentVisible.value = true
  assignmentLoading.value = true
  try {
    assignmentCandidates.value = await PartnerApi.getAssignmentCandidates()
  } catch {
    assignmentCandidates.value = []
    message.error('候选员工加载失败')
  } finally {
    assignmentLoading.value = false
  }
}
const submitAssignment = async () => {
  const row = assignmentPartner.value
  if (!row || !assignmentReason.value.trim()) return
  assignmentLoading.value = true
  try {
    await PartnerApi.updateAssignment(row.id, {
      assignedUserId: assignmentUserId.value,
      reason: assignmentReason.value.trim(),
      expectedVersion: row.assignmentVersion
    })
    message.success(assignmentUserId.value ? '兼职归属已更新' : '兼职归属已解除')
    assignmentVisible.value = false
    await load()
  } finally {
    assignmentLoading.value = false
  }
}
const openAssignmentLogs = async (row: PartnerApi.PartnerVO) => {
  assignmentLogVisible.value = true
  assignmentLogLoading.value = true
  try {
    assignmentLogs.value = (await PartnerApi.getAssignmentLogPage(row.id)).list
  } catch {
    assignmentLogs.value = []
    message.error('归属历史加载失败')
  } finally {
    assignmentLogLoading.value = false
  }
}
const changeMobile = async (row: PartnerApi.PartnerVO) => {
  const result = await message.prompt('请输入新的登录手机号', '修改手机号')
  const mobile = result.value.trim()
  if (!/^1\d{10}$/.test(mobile)) return message.warning('手机号格式不正确')
  await PartnerApi.updatePartnerMobile(row.id, mobile)
  message.success('手机号已修改，原 Partner Token 已撤销')
  await load()
}
const resetPassword = async (row: PartnerApi.PartnerVO) => {
  const result = await message.prompt('请输入 8-20 位且包含字母和数字的新密码', '重置密码')
  if (!/^(?=.*[A-Za-z])(?=.*\d).{8,20}$/.test(result.value))
    return message.warning('密码格式不正确')
  await PartnerApi.resetPartnerPassword(row.id, result.value)
  message.success('密码已重置，原 Partner Token 已撤销')
}
onMounted(() => {
  load()
  loadInvitations()
})
</script>
<style scoped>
.table {
  margin-top: 16px;
}
</style>
