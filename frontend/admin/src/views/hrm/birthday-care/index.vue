<template>
  <ContentWrap>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="mb-4"><template #default><el-button link type="primary" @click="load">重试</el-button></template></el-alert>
    <el-form v-loading="loading" :model="form" label-width="150px" class="max-w-900px">
      <el-card v-for="item in reminderItems" :key="item.key" class="mb-4" shadow="never">
        <template #header>{{ item.label }}</template>
        <el-form-item :label="`启用${item.label}`"><el-switch v-model="form[item.key].enabled" /></el-form-item>
        <el-form-item label="提前天数"><el-input-number v-model="form[item.key].advanceDays" :min="0" :max="30" /><span class="ml-2 text-gray-500">当天填 0</span></el-form-item>
        <el-form-item label="每日触发时间"><el-time-picker v-model="form[item.key].triggerTime" format="HH:mm" value-format="HH:mm" :clearable="false" /></el-form-item>
        <el-form-item label="接收部门"><DeptSelect v-model="form[item.key].deptIds" multiple class="w-full" /></el-form-item>
        <el-form-item label="包含下级部门"><el-switch v-model="form[item.key].includeChildDepartments" /></el-form-item>
        <el-alert v-if="form[item.key]?.missingTaskPermissionUserIds?.length" type="warning" :closable="false">{{ form[item.key]?.missingTaskPermissionUserIds?.length }} 名接收人没有业务待办查询权限，仍会收到站内信，但看不到待办。</el-alert>
      </el-card>
      <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
    </el-form>
  </ContentWrap>
</template>
<script setup lang="ts">
import * as ReminderApi from '@/api/hrm/birthdayCare'
import DeptSelect from '@/views/system/dept/components/DeptSelect.vue'
defineOptions({ name: 'HrmBirthdayCare' })
const message = useMessage()
const loading = ref(false); const saving = ref(false); const error = ref('')
const defaults = (): ReminderApi.ReminderRule => ({ enabled: false, advanceDays: 1, triggerTime: '09:00', deptIds: [], includeChildDepartments: false, recipientUserIds: [], missingTaskPermissionUserIds: [] })
const form = reactive<ReminderApi.EmployeeReminderConfig>({ birthday: defaults(), contractExpiry: defaults(), entryAnniversary: defaults() })
const reminderItems = [{ key: 'birthday', label: '生日关怀' }, { key: 'contractExpiry', label: '合同到期提醒' }, { key: 'entryAnniversary', label: '入职周年提醒' }] as const
const load = async () => { loading.value = true; error.value = ''; try { Object.assign(form, await ReminderApi.getEmployeeReminderConfig()) } catch (e: any) { error.value = e?.msg || e?.message || '员工提醒配置加载失败' } finally { loading.value = false } }
const save = async () => { saving.value = true; try { await ReminderApi.saveEmployeeReminderConfig(form); message.success('保存成功'); await load() } finally { saving.value = false } }
onMounted(load)
</script>
