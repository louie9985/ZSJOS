<template>
  <ContentWrap>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="mb-4">
      <template #default><el-button link type="primary" @click="load">重试</el-button></template>
    </el-alert>
    <el-form v-loading="loading" ref="formRef" :model="form" label-width="150px" class="max-w-720px">
      <el-form-item label="启用生日关怀">
        <el-switch v-model="form.enabled" />
      </el-form-item>
      <el-form-item label="提前天数" prop="advanceDays">
        <el-input-number v-model="form.advanceDays" :min="0" :max="30" />
        <span class="ml-2 text-gray-500">生日当天填 0</span>
      </el-form-item>
      <el-form-item label="每日触发时间" prop="triggerTime">
        <el-time-picker v-model="form.triggerTime" format="HH:mm" value-format="HH:mm" :clearable="false" />
      </el-form-item>
      <el-form-item label="接收部门" prop="deptIds">
        <DeptSelect v-model="form.deptIds" multiple class="w-full" />
      </el-form-item>
      <el-form-item label="包含下级部门">
        <el-switch v-model="form.includeChildDepartments" />
      </el-form-item>
      <el-form-item label="接收人权限提示">
        <el-alert v-if="form.missingTaskPermissionUserIds?.length" type="warning" :closable="false">
          {{ form.missingTaskPermissionUserIds.length }} 名接收人没有业务待办查询权限，仍会收到站内信，但看不到待办。
        </el-alert>
        <span v-else class="text-gray-500">当前接收人均具备业务待办查询权限</span>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>
</template>

<script setup lang="ts">
import * as BirthdayCareApi from '@/api/hrm/birthdayCare'
import DeptSelect from '@/views/system/dept/components/DeptSelect.vue'

defineOptions({ name: 'HrmBirthdayCare' })
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const form = reactive<BirthdayCareApi.BirthdayCareConfig>({
  enabled: false, advanceDays: 1, triggerTime: '09:00', deptIds: [], includeChildDepartments: false,
  recipientUserIds: [], missingTaskPermissionUserIds: []
})

const load = async () => {
  loading.value = true; error.value = ''
  try { Object.assign(form, await BirthdayCareApi.getBirthdayCareConfig()) }
  catch (e: any) { error.value = e?.msg || e?.message || '生日关怀配置加载失败' }
  finally { loading.value = false }
}
const save = async () => {
  saving.value = true
  try { await BirthdayCareApi.saveBirthdayCareConfig(form); message.success('保存成功'); await load() }
  finally { saving.value = false }
}
onMounted(load)
</script>
