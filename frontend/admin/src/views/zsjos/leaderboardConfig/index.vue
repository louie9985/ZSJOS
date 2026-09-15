<template>
  <ContentWrap>
    <el-alert v-if="error" title="排行榜配置加载失败，请重试" type="error" show-icon class="mb-16px" />
    <el-alert v-if="roleError" title="系统角色加载失败，暂时无法配置员工角色" type="warning" show-icon class="mb-16px" />
    <el-alert v-else-if="form && roles.length === 0" title="暂无可用系统角色" type="info" show-icon class="mb-16px" />
    <el-form v-if="form" v-loading="loading" label-width="180px" class="max-w-700px">
      <el-form-item label="排行榜显示开启"><el-switch v-model="form.enabled" /></el-form-item>
      <el-form-item label="内部员工纳入排行榜"><el-switch v-model="form.includeEmployeeSubmitter" /></el-form-item>
      <el-form-item label="纳入的系统角色">
        <el-select v-model="form.employeeRoleCodes" multiple filterable class="w-400px" :disabled="!form.includeEmployeeSubmitter">
          <el-option v-for="role in roles" :key="role.code" :label="role.name" :value="role.code" />
        </el-select>
      </el-form-item>
      <el-form-item label="启用榜单"><el-checkbox-group v-model="form.enabledTypes"><el-checkbox v-for="item in types" :key="item.value" :label="item.value">{{ item.label }}</el-checkbox></el-checkbox-group></el-form-item>
      <el-form-item label="默认榜单"><el-select v-model="form.defaultType"><el-option v-for="item in types" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
      <el-form-item label="默认展示周期"><el-select v-model="form.defaultPeriod"><el-option v-for="item in periods" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
      <el-button type="primary" :loading="saving" v-hasPermi="['zsjos:partner:leaderboard-config:update']" @click="save">保存</el-button>
    </el-form>
    <el-empty v-else-if="!loading" description="暂无排行榜配置"><el-button @click="load">重试</el-button></el-empty>
  </ContentWrap>
</template>
<script setup lang="ts">
import * as Api from '@/api/zsjos/leaderboard'
import * as RoleApi from '@/api/system/role'
import { useMessage } from '@/hooks/web/useMessage'
const message = useMessage(); const loading = ref(false); const saving = ref(false); const error = ref(false); const roleError = ref(false)
const form = ref<Api.LeaderboardConfigVO>(); const roles = ref<RoleApi.RoleVO[]>([])
const types = [{ value: 'estimated_income', label: '预计收益' }, { value: 'withdrawn_amount', label: '已提现金额' }, { value: 'lead_count', label: '客资数' }, { value: 'valid_lead_count', label: '有效客资数' }] as const
const periods = [{ value: 'today', label: '日榜' }, { value: 'week', label: '周榜' }, { value: 'month', label: '月榜' }, { value: 'total', label: '总榜' }] as const
const load = async () => { loading.value = true; error.value = false; roleError.value = false; try { form.value = await Api.getLeaderboardConfig() } catch { error.value = true; form.value = undefined } try { roles.value = await RoleApi.getSimpleRoleList() } catch { roleError.value = true; roles.value = [] } finally { loading.value = false } }
const save = async () => { if (!form.value || form.value.enabledTypes.length === 0 || !form.value.enabledTypes.includes(form.value.defaultType)) return message.error('至少启用一个榜单，且默认榜单必须已启用'); saving.value = true; try { await Api.saveLeaderboardConfig(form.value); message.success('已保存') } catch { message.error('保存失败，请重试') } finally { saving.value = false } }
onMounted(load)
</script>
