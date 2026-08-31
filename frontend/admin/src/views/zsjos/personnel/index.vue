<template>
  <ContentWrap>
    <el-form :inline="true" @submit.prevent>
      <el-form-item label="人员账号">
        <el-select
          v-model="selectedUserId"
          filterable
          placeholder="请选择人员"
          style="width: 280px"
          @change="loadState"
        >
          <el-option
            v-for="user in users"
            :key="user.id"
            :label="`${user.nickname} (${user.username})`"
            :value="user.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item><el-button :loading="loading" @click="loadState">刷新</el-button></el-form-item>
    </el-form>
    <el-alert v-if="usersError" :title="usersError" type="error" show-icon>
      <template #default><el-button link @click="loadUsers">重试</el-button></template>
    </el-alert>
    <el-empty v-else-if="!selectedUserId" description="请选择人员" />
    <el-descriptions v-else-if="state" :column="2" border>
      <el-descriptions-item label="业务状态">{{ stateLabels[state.state] }}</el-descriptions-item>
      <el-descriptions-item label="变更时间">{{
        state.changedAt || '未变更'
      }}</el-descriptions-item>
      <el-descriptions-item label="最近原因" :span="2">{{
        state.reason || '无'
      }}</el-descriptions-item>
    </el-descriptions>
    <el-alert v-else-if="error" :title="error" type="error" show-icon
      ><template #default><el-button link @click="loadState">重试</el-button></template></el-alert
    >
    <div v-if="state" class="actions">
      <el-button
        v-hasPermi="['zsjos:personnel:update-state']"
        type="success"
        @click="changeState('enabled')"
        >启用</el-button
      >
      <el-button
        v-hasPermi="['zsjos:personnel:update-state']"
        type="warning"
        @click="changeState('disabled')"
        >停用</el-button
      >
      <el-button
        v-hasPermi="['zsjos:personnel:update-state']"
        type="danger"
        @click="changeState('departed')"
        >离职</el-button
      >
    </div>
  </ContentWrap>
</template>
<script lang="ts" setup>
import * as UserApi from '@/api/system/user'
import * as PersonnelApi from '@/api/zsjos/personnel'

defineOptions({ name: 'ZsjosPersonnel' })
const message = useMessage()
const users = ref<UserApi.UserVO[]>([])
const selectedUserId = ref<number>()
const state = ref<PersonnelApi.PersonnelStateVO>()
const loading = ref(false)
const error = ref('')
const usersError = ref('')
const stateLabels = { enabled: '启用', disabled: '停用', departed: '离职' }

const loadState = async () => {
  if (!selectedUserId.value) return
  loading.value = true
  error.value = ''
  try {
    state.value = await PersonnelApi.getPersonnelState(selectedUserId.value)
  } catch (e) {
    state.value = undefined
    error.value = '人员状态加载失败'
  } finally {
    loading.value = false
  }
}
const changeState = async (next: PersonnelApi.PersonnelStateVO['state']) => {
  if (!selectedUserId.value) return
  const result = await message.prompt('请输入状态变更原因', '变更人员状态')
  const reason = result.value.trim()
  if (!reason) return message.warning('变更原因不能为空')
  await PersonnelApi.updatePersonnelState(selectedUserId.value, next, reason)
  message.success('人员状态已更新')
  await loadState()
}
const loadUsers = async () => {
  usersError.value = ''
  try {
    users.value = await UserApi.getSimpleUserList()
  } catch {
    users.value = []
    usersError.value = '人员列表加载失败'
  }
}
onMounted(loadUsers)
</script>
<style scoped>
.actions {
  margin-top: 16px;
  display: flex;
  gap: 8px;
}
</style>
