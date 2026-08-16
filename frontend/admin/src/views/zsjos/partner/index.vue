<template>
  <ContentWrap>
    <el-button v-hasPermi="['zsjos:partner:create']" type="primary" @click="createVisible = true"
      >新增兼职</el-button
    >
    <el-table v-loading="loading" :data="partners" class="table">
      <el-table-column prop="partnerNo" label="兼职编号" />
      <el-table-column prop="name" label="姓名" />
      <el-table-column prop="mobile" label="手机号" />
      <el-table-column label="状态"
        ><template #default="scope">{{ statusLabels[scope.row.status] }}</template></el-table-column
      >
      <el-table-column label="操作" width="260">
        <template #default="scope">
          <el-button
            v-if="scope.row.status === 'enabled'"
            v-hasPermi="['zsjos:partner:update-state']"
            link
            type="warning"
            @click="changeState(scope.row, false)"
            >停用</el-button
          >
          <el-button
            v-if="scope.row.status === 'disabled'"
            v-hasPermi="['zsjos:partner:update-state']"
            link
            type="success"
            @click="changeState(scope.row, true)"
            >启用</el-button
          >
          <el-button
            v-if="scope.row.status === 'enabled'"
            v-hasPermi="['zsjos:partner:convert']"
            link
            type="primary"
            @click="openConvert(scope.row)"
            >转为员工</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <el-alert v-if="loadError" :title="loadError" type="error" show-icon>
      <template #default><el-button link @click="load">重试</el-button></template>
    </el-alert>
    <el-empty v-else-if="!loading && !partners.length" description="暂无兼职主体" />
  </ContentWrap>
  <Dialog v-model="createVisible" title="新增兼职账号">
    <el-form :model="createForm" label-width="100px">
      <el-form-item label="兼职编号"><el-input v-model="createForm.partnerNo" /></el-form-item>
      <el-form-item label="姓名"><el-input v-model="createForm.name" /></el-form-item>
      <el-form-item label="手机号"
        ><el-input v-model="createForm.mobile" maxlength="11"
      /></el-form-item>
      <el-form-item label="用户名" required
        ><el-input v-model="createForm.username" maxlength="32"
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
  <Dialog v-model="convertVisible" title="兼职转为新媒体员工">
    <el-form :model="convertForm" label-width="120px">
      <el-form-item label="员工类型" required
        ><el-radio-group v-model="convertForm.targetType"
          ><el-radio-button value="new_media_employee">新媒体员工</el-radio-button
          ><el-radio-button value="new_media_manager">新媒体主管</el-radio-button></el-radio-group
        ></el-form-item
      >
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
</template>
<script lang="ts" setup>
import * as PartnerApi from '@/api/zsjos/partner'
import * as DeptApi from '@/api/system/dept'
import { defaultProps, handleTree } from '@/utils/tree'

defineOptions({ name: 'ZsjosPartner' })
const message = useMessage()
const loading = ref(false)
const partners = ref<PartnerApi.PartnerVO[]>([])
const loadError = ref('')
const statusLabels = { enabled: '启用', disabled: '停用', converted: '已转员工' }
const createVisible = ref(false)
const convertVisible = ref(false)
const selectedId = ref<number>()
const deptList = ref<Tree[]>([])
const createForm = reactive<PartnerApi.PartnerCreateVO>({
  partnerNo: '',
  name: '',
  mobile: '',
  username: '',
  password: '',
  channelId: ''
})
const convertForm = reactive({
  targetType: 'new_media_employee',
  deptId: undefined as number | undefined,
  migrateHistoricalOrganization: false,
  reason: ''
})
const load = async () => {
  loading.value = true
  loadError.value = ''
  try {
    partners.value = await PartnerApi.getPartnerList()
  } catch {
    partners.value = []
    loadError.value = '兼职列表加载失败'
  } finally {
    loading.value = false
  }
}
const submitCreate = async () => {
  if (
    !/^[A-Za-z0-9_]{4,32}$/.test(createForm.username) ||
    !/^(?=.*[A-Za-z])(?=.*\d).{8,20}$/.test(createForm.password)
  )
    return message.warning('请检查用户名及密码规则')
  await PartnerApi.createPartner(createForm)
  createVisible.value = false
  message.success('兼职账号已创建')
  await load()
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
  if (!selectedId.value || !convertForm.deptId || !convertForm.reason.trim())
    return message.warning('请完整填写转换信息')
  await PartnerApi.convertPartner(selectedId.value, { ...convertForm, deptId: convertForm.deptId })
  convertVisible.value = false
  message.success('兼职已转为员工')
  await load()
}
onMounted(load)
</script>
<style scoped>
.table {
  margin-top: 16px;
}
</style>
