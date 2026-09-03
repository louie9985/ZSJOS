<template>
  <ContentWrap>
    <el-alert title="通知渠道启用后，业务规则才会尝试投递；企业微信凭据仍在“社交客户端”维护。" type="info" show-icon />
  </ContentWrap>
  <ContentWrap>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false">
      <template #default><el-button link type="primary" @click="load">重试</el-button></template>
    </el-alert>
    <el-table v-loading="loading" :data="config ? [config] : []">
      <el-table-column label="渠道" width="160"><template #default>企业微信</template></el-table-column>
      <el-table-column label="配置来源" prop="configRef" width="200" />
      <el-table-column label="配置说明" prop="maskedConfig" min-width="240" />
      <el-table-column label="状态" width="120">
        <template #default="scope"><el-tag :type="scope.row.enabled ? 'success' : 'info'">{{ scope.row.enabled ? '已启用' : '已停用' }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="scope">
          <el-switch v-model="scope.row.enabled" v-hasPermi="['system:notify-channel:update']" @change="toggle(scope.row.enabled)" />
        </template>
      </el-table-column>
      <template #empty><el-empty description="尚未创建企业微信渠道配置" /></template>
    </el-table>
    <el-divider />
    <el-descriptions :column="1" border>
      <el-descriptions-item label="企业微信应用">请到“系统管理 → 社交通讯 → 社交客户端”维护 CorpID、Secret、AgentID，并启用管理员类型客户端。</el-descriptions-item>
      <el-descriptions-item label="接收人">员工还需绑定企业微信并打开个人“接收企业微信推送”，否则会被跳过。</el-descriptions-item>
    </el-descriptions>
  </ContentWrap>
</template>

<script setup lang="ts">
import * as ChannelApi from '@/api/system/notify/channel'

defineOptions({ name: 'SystemNotifyChannel' })
const message = useMessage()
const loading = ref(false)
const error = ref('')
const config = ref<ChannelApi.NotifyChannelConfigVO | null>(null)

const load = async () => {
  loading.value = true
  error.value = ''
  try { config.value = await ChannelApi.getNotifyChannelConfig() }
  catch (e: any) { error.value = e?.msg || e?.message || '通知渠道加载失败' }
  finally { loading.value = false }
}
const toggle = async (enabled: boolean) => {
  try {
    await ChannelApi.updateNotifyChannelEnabled('wecom', enabled)
    message.success(enabled ? '企业微信渠道已启用' : '企业微信渠道已停用')
    await load()
  } catch { await load() }
}
onMounted(load)
</script>
