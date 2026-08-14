<template>
  <ContentWrap>
    <el-alert
      title="维护模式开启后，除固定认证与回调接口外，所有写请求将返回 503；只读请求不受影响。"
      type="warning"
      :closable="false"
      show-icon
      class="mb-16px"
    />

    <div v-loading="loading" class="maintenance-setting">
      <el-result v-if="error" icon="error" title="维护状态加载失败" :sub-title="error">
        <template #extra><el-button type="primary" @click="load">重试</el-button></template>
      </el-result>
      <el-empty v-else-if="state === undefined" description="暂无维护状态" />
      <el-descriptions v-else :column="1" border>
        <el-descriptions-item label="当前状态">
          <el-tag :type="state ? 'danger' : 'success'">{{ state ? '维护中' : '正常运行' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="维护开关">
          <el-switch
            :model-value="state"
            :loading="updating"
            active-text="开启"
            inactive-text="关闭"
            @change="changeState"
          />
        </el-descriptions-item>
      </el-descriptions>
    </div>
  </ContentWrap>
</template>

<script lang="ts" setup>
import * as MaintenanceApi from '@/api/system/maintenance'

defineOptions({ name: 'SystemMaintenance' })

const message = useMessage()
const loading = ref(false)
const updating = ref(false)
const state = ref<boolean>()
const error = ref('')

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    state.value = (await MaintenanceApi.getMaintenanceMode()).enabled
  } catch (e: any) {
    state.value = undefined
    error.value = e?.msg || e?.message || '请稍后重试'
  } finally {
    loading.value = false
  }
}

const changeState = async (enabled: boolean | string | number) => {
  const next = Boolean(enabled)
  try {
    await message.confirm(
      next ? '开启后普通写操作将立即停止，确认开启维护模式？' : '确认关闭维护模式并恢复普通写操作？'
    )
    updating.value = true
    await MaintenanceApi.updateMaintenanceMode(next)
    state.value = next
    message.success(next ? '维护模式已开启' : '维护模式已关闭')
  } catch {
    // Cancelled confirmation or a failed request keeps the authoritative server state.
    await load()
  } finally {
    updating.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.maintenance-setting {
  min-height: 160px;
  max-width: 720px;
}
</style>
