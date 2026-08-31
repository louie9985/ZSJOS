<template>
  <ContentWrap>
    <el-alert
      v-if="active"
      :title="`正在以 ${active.targetNameSnapshot} 的权限只读查看`"
      description="借视图期间所有 ZSJOS 写操作均被服务端拒绝；30 分钟无活动后会话自动失效。"
      type="warning"
      :closable="false"
      show-icon
      class="mb-16px"
    />

    <el-descriptions v-if="active" :column="1" border class="max-w-720px">
      <el-descriptions-item label="目标账号">{{ active.targetNameSnapshot }}</el-descriptions-item>
      <el-descriptions-item label="开始时间">{{ active.startedAt }}</el-descriptions-item>
      <el-descriptions-item label="借用原因">{{ active.reason }}</el-descriptions-item>
      <el-descriptions-item label="操作">
        <el-button type="danger" :loading="loading" @click="end">结束借视图</el-button>
      </el-descriptions-item>
    </el-descriptions>

    <el-form v-else label-width="100px" class="max-w-720px" @submit.prevent>
      <el-alert v-if="error" :title="error" type="error" show-icon class="mb-16px">
        <template #default><el-button link @click="loadUsers">重试</el-button></template>
      </el-alert>
      <el-form-item label="目标账号" required>
        <el-select
          v-model="targetUserId"
          filterable
          :loading="usersLoading"
          loading-text="正在加载启用账号"
          no-data-text="暂无启用账号"
          placeholder="请选择启用账号"
          class="!w-320px"
        >
          <el-option v-for="user in users" :key="user.id" :label="user.nickname" :value="user.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="借用原因" required>
        <el-input v-model="reason" type="textarea" maxlength="500" show-word-limit :rows="3" />
      </el-form-item>
      <el-form-item>
        <el-button
          v-hasPermi="['zsjos:impersonation:start']"
          type="primary"
          :loading="loading"
          :disabled="!targetUserId || !reason.trim()"
          @click="start"
        >
          开始只读借视图
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>
</template>

<script lang="ts" setup>
import * as UserApi from '@/api/system/user'
import * as ImpersonationApi from '@/api/zsjos/impersonation'
import { getStoredImpersonation, IMPERSONATION_CHANGE_EVENT } from '@/utils/impersonation'

defineOptions({ name: 'ZsjosImpersonation' })

const message = useMessage()
const active = ref(ImpersonationApi.getStoredImpersonation())
const users = ref<UserApi.UserSimpleVO[]>([])
const targetUserId = ref<number>()
const reason = ref('')
const loading = ref(false)
const usersLoading = ref(false)
const error = ref('')

const loadUsers = async () => {
  usersLoading.value = true
  error.value = ''
  try {
    users.value = await UserApi.getSimpleUserOptions()
  } catch (e: any) {
    users.value = []
    error.value = e?.msg || e?.message || '账号列表加载失败'
  } finally {
    usersLoading.value = false
  }
}

const start = async () => {
  if (!targetUserId.value || !reason.value.trim()) return
  loading.value = true
  try {
    const session = await ImpersonationApi.startImpersonation(
      targetUserId.value,
      reason.value.trim()
    )
    ImpersonationApi.storeImpersonation(session)
    active.value = session
    message.success('只读借视图已开启')
  } finally {
    loading.value = false
  }
}

const end = async () => {
  if (!active.value) return
  loading.value = true
  try {
    await ImpersonationApi.endImpersonation(active.value.id)
    ImpersonationApi.storeImpersonation()
    active.value = undefined
    message.success('借视图已结束')
  } finally {
    loading.value = false
  }
}

const syncActive = () => {
  active.value = getStoredImpersonation()
  if (!active.value) loadUsers()
}

onMounted(() => {
  window.addEventListener(IMPERSONATION_CHANGE_EVENT, syncActive)
  if (!active.value) loadUsers()
})
onBeforeUnmount(() => window.removeEventListener(IMPERSONATION_CHANGE_EVENT, syncActive))
</script>
