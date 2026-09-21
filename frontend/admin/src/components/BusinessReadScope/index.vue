<template>
  <el-space wrap class="mb-16px">
    <el-select :model-value="modelValue.readScope" aria-label="查看范围" style="width: 180px" @update:model-value="scope => emit('update:modelValue', { readScope: scope })">
      <el-option label="本人" value="SELF" />
      <el-option label="全部（只读）" value="ALL" />
      <el-option label="指定人员（只读）" value="USER" />
    </el-select>
    <el-select v-if="modelValue.readScope === 'USER'" :model-value="modelValue.targetUserId" aria-label="指定人员" placeholder="请选择人员" filterable :loading="loading" style="width: 180px" @update:model-value="id => emit('update:modelValue', { ...modelValue, targetUserId: id })">
      <el-option v-for="user in users" :key="user.id" :value="user.id" :label="user.nickname" />
    </el-select>
    <span v-if="modelValue.readScope !== 'SELF'">只读查看，不代替他人办理业务</span>
    <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button @click="load">重试</el-button></el-alert>
  </el-space>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import { getSimpleUserOptions, type UserSimpleVO } from '@/api/system/user'
const props = defineProps<{ modelValue: { readScope: 'SELF' | 'ALL' | 'USER'; targetUserId?: number } }>()
const emit = defineEmits<{ 'update:modelValue': [value: { readScope: 'SELF' | 'ALL' | 'USER'; targetUserId?: number }] }>()
const users = ref<UserSimpleVO[]>([]), loading = ref(false), error = ref('')
const load = async () => {
  loading.value = true; error.value = ''
  try { users.value = await getSimpleUserOptions(true) }
  catch (cause: unknown) { error.value = cause instanceof Error ? cause.message : '人员加载失败' }
  finally { loading.value = false }
}
watch(() => props.modelValue.readScope, scope => { if (scope === 'USER') void load() }, { immediate: true })
</script>
